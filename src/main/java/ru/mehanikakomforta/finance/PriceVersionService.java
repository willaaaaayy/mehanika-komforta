package ru.mehanikakomforta.finance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mehanikakomforta.clients.ClientAccess;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PriceVersionService {

    private static final ZoneId SARATOV = ZoneId.of("Europe/Saratov");

    private final PriceVersionRepository versions;
    private final PriceVersionLineRepository lines;
    private final WorkObjectRepository objects;
    private final ContractRepository contracts;
    private final InternalRates rates;

    public PriceVersionService(
            PriceVersionRepository versions,
            PriceVersionLineRepository lines,
            WorkObjectRepository objects,
            ContractRepository contracts,
            InternalRates rates
    ) {
        this.versions = versions;
        this.lines = lines;
        this.objects = objects;
        this.contracts = contracts;
        this.rates = rates;
    }

    @Transactional
    public List<VersionView> list(long objectId) {
        if (objects.findById(objectId).isEmpty()) {
            return null;
        }
        ensureBaseline(objectId);
        return views(objectId);
    }

    @Transactional
    public VersionView add(long objectId, String reason, List<IncomingLine> incoming) {
        if (objects.findById(objectId).isEmpty() || reason == null || reason.isBlank() || incoming == null || incoming.isEmpty()) {
            return null;
        }
        ensureBaseline(objectId);
        List<PriceVersionEntity> existing = versions.findByObjectIdOrderByVersionNoDesc(objectId);
        int next = existing.isEmpty() ? 1 : existing.get(0).getVersionNo() + 1;
        Map<String, DraftLine> picture = existing.isEmpty() ? new LinkedHashMap<>() : picture(existing.get(0).getId());
        for (IncomingLine item : incoming) {
            if (item.title() == null || item.title().isBlank()) {
                continue;
            }
            apply(picture, item, LocalDate.now(SARATOV));
        }
        if (picture.isEmpty()) {
            return null;
        }
        BigDecimal amount = total(picture);
        PriceVersionEntity saved = versions.save(new PriceVersionEntity(
                objectId, next, LocalDate.now(SARATOV), ClientAccess.actor(), reason.trim(), amount, false
        ));
        store(saved.getId(), picture);
        return view(saved);
    }

    public CompareView compare(long objectId, long fromId, long toId) {
        PriceVersionEntity from = versions.findById(fromId).orElse(null);
        PriceVersionEntity to = versions.findById(toId).orElse(null);
        if (from == null || to == null || !from.getObjectId().equals(objectId) || !to.getObjectId().equals(objectId)) {
            return null;
        }
        Map<String, DraftLine> older = picture(from.getId());
        Map<String, DraftLine> newer = picture(to.getId());
        List<CompareLine> rows = new ArrayList<>();
        for (String title : older.keySet()) {
            rows.add(new CompareLine(title, moneyOf(older.get(title)), newer.containsKey(title) ? moneyOf(newer.get(title)) : null));
        }
        for (String title : newer.keySet()) {
            if (!older.containsKey(title)) {
                rows.add(new CompareLine(title, null, moneyOf(newer.get(title))));
            }
        }
        BigDecimal difference = null;
        if (from.getAmount() != null && to.getAmount() != null) {
            difference = to.getAmount().subtract(from.getAmount());
        }
        return new CompareView(new AmountChange(from.getAmount(), to.getAmount()), difference, rows);
    }

    @Transactional
    public VersionView approve(long objectId, long versionId) {
        PriceVersionEntity version = versions.findById(versionId).orElse(null);
        if (version == null || !version.getObjectId().equals(objectId)) {
            return null;
        }
        if (version.isApproved()) {
            return view(version);
        }
        List<ContractEntity> linked = contracts.findByObjectIdAndConfirmedTrueOrderByCreatedAtDesc(objectId);
        if (linked.isEmpty() || version.getAmount() == null) {
            return null;
        }
        BigDecimal current = BigDecimal.ZERO;
        for (ContractEntity contract : linked) {
            current = current.add(contract.getAmount());
        }
        BigDecimal delta = version.getAmount().subtract(current);
        ContractEntity contract = linked.get(0);
        contract.fill(contract.getNumber(), contract.getClientName(), contract.getClientInn(), contract.getObjectAddress(), contract.getAmount().add(delta), true);
        if (delta.signum() > 0) {
            contract.getSchedule().add(new ScheduleEntity(contract, "Доплата по допсоглашению", delta, null, "final", contract.getSchedule().size()));
        } else if (delta.signum() < 0) {
            reduceUnpaid(contract, delta.abs());
        }
        contracts.save(contract);
        String suffix = "-ДС-" + version.getVersionNo();
        version.approve(contract.getNumber() + suffix, contract.getNumber() + "-АКТ-" + version.getVersionNo(), delta.signum() == 0 ? null : delta);
        return view(versions.save(version));
    }

    public VersionView one(long objectId, long versionId) {
        PriceVersionEntity version = versions.findById(versionId).orElse(null);
        if (version == null || !version.getObjectId().equals(objectId)) {
            return null;
        }
        return view(version);
    }

    private void ensureBaseline(long objectId) {
        if (!versions.findByObjectIdOrderByVersionNoDesc(objectId).isEmpty()) {
            return;
        }
        List<ContractEntity> linked = contracts.findByObjectIdAndConfirmedTrueOrderByCreatedAtDesc(objectId);
        if (linked.isEmpty()) {
            return;
        }
        BigDecimal amount = BigDecimal.ZERO;
        StringBuilder reason = new StringBuilder("По договору");
        for (ContractEntity contract : linked) {
            amount = amount.add(contract.getAmount());
            if (contract.getNumber() != null) {
                reason.append(' ').append(contract.getNumber());
            }
        }
        PriceVersionEntity baseline = versions.save(new PriceVersionEntity(
                objectId, 1, LocalDate.now(SARATOV), "договор", reason.toString().trim(), amount, true
        ));
        ContractEntity first = linked.get(0);
        lines.save(new PriceVersionLineEntity(
                baseline.getId(),
                "Договор " + (first.getNumber() == null ? "" : first.getNumber()),
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                amount,
                null,
                null
        ));
    }

    private void apply(Map<String, DraftLine> picture, IncomingLine item, LocalDate day) {
        String title = item.title().trim();
        DraftLine previous = picture.get(title);
        BigDecimal quantity = item.quantity() != null ? item.quantity() : previous == null ? null : previous.quantity;
        BigDecimal price = item.clientPrice() != null ? item.clientPrice() : previous == null ? null : previous.clientPrice;
        String change = item.change();
        if ("removed".equals(change)) {
            picture.put(title, new DraftLine(title, "removed", previous == null ? null : previous.quantity, previous == null ? null : previous.quantity, null, previous == null ? price : previous.clientPrice, null, null, true));
            return;
        }
        BigDecimal plan = previous == null ? null : previous.quantity;
        BigDecimal fact = quantity;
        BigDecimal cost = cost(title, fact, day);
        BigDecimal lineMoney = money(fact, price);
        BigDecimal margin = lineMoney != null && cost != null ? lineMoney.subtract(cost) : null;
        picture.put(title, new DraftLine(title, change, quantity, plan, fact, price, cost, margin, false));
    }

    private BigDecimal cost(String title, BigDecimal quantity, LocalDate day) {
        if (quantity == null) {
            return null;
        }
        String workId = null;
        for (InternalRates.RateView rate : rates.catalog("standard").rates()) {
            if (title.equalsIgnoreCase(rate.title())) {
                workId = rate.workId();
                break;
            }
        }
        BigDecimal labor = rates.laborAt(workId, day);
        if (labor == null) {
            return null;
        }
        return quantity.multiply(labor).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal quantity, BigDecimal price) {
        if (price == null) {
            return null;
        }
        if (quantity == null) {
            return price;
        }
        return quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal total(Map<String, DraftLine> picture) {
        BigDecimal amount = null;
        for (DraftLine line : picture.values()) {
            if (line.removed) {
                continue;
            }
            amount = add(amount, money(line.fact, line.clientPrice));
        }
        return amount;
    }

    private void store(Long versionId, Map<String, DraftLine> picture) {
        for (DraftLine line : picture.values()) {
            lines.save(new PriceVersionLineEntity(versionId, line.title, line.change, line.quantity, line.plan, line.fact, line.clientPrice, line.cost, line.margin));
        }
    }

    private Map<String, DraftLine> picture(Long versionId) {
        Map<String, DraftLine> picture = new LinkedHashMap<>();
        for (PriceVersionLineEntity line : lines.findByVersionIdOrderByIdAsc(versionId)) {
            picture.put(line.getTitle(), new DraftLine(
                    line.getTitle(),
                    line.getChangeKind(),
                    line.getQuantity(),
                    line.getPlanQty(),
                    line.getFactQty(),
                    line.getClientPrice(),
                    line.getCost(),
                    line.getMargin(),
                    "removed".equals(line.getChangeKind())
            ));
        }
        return picture;
    }

    private static void reduceUnpaid(ContractEntity contract, BigDecimal left) {
        List<ScheduleEntity> doomed = new ArrayList<>();
        for (int index = contract.getSchedule().size() - 1; index >= 0 && left.signum() > 0; index--) {
            ScheduleEntity step = contract.getSchedule().get(index);
            if (step.getPaidOn() != null) {
                continue;
            }
            BigDecimal cut = step.getAmount().min(left);
            left = left.subtract(cut);
            if (step.getAmount().compareTo(cut) == 0) {
                doomed.add(step);
            } else {
                step.replace(step.getTitle(), step.getAmount().subtract(cut), step.getDueOn(), step.getKind(), step.getPosition());
            }
        }
        contract.getSchedule().removeAll(doomed);
    }

    private List<VersionView> views(long objectId) {
        List<VersionView> body = new ArrayList<>();
        for (PriceVersionEntity version : versions.findByObjectIdOrderByVersionNoDesc(objectId)) {
            body.add(view(version));
        }
        return body;
    }

    private VersionView view(PriceVersionEntity version) {
        List<LineView> rows = new ArrayList<>();
        for (PriceVersionLineEntity line : lines.findByVersionIdOrderByIdAsc(version.getId())) {
            rows.add(new LineView(
                    line.getTitle(),
                    line.getChangeKind(),
                    line.getQuantity(),
                    line.getPlanQty(),
                    line.getFactQty(),
                    line.getClientPrice(),
                    line.getCost(),
                    line.getMargin()
            ));
        }
        return new VersionView(
                version.getId(),
                version.getVersionNo(),
                version.getCreatedOn().toString(),
                version.getAuthor(),
                version.getReason(),
                version.getAmount(),
                version.isApproved(),
                version.getAddendumNumber(),
                version.getActNumber(),
                version.getInvoiceAmount(),
                rows
        );
    }

    private static BigDecimal moneyOf(DraftLine line) {
        if (line == null || line.removed) {
            return null;
        }
        return money(line.fact, line.clientPrice);
    }

    private static BigDecimal add(BigDecimal left, BigDecimal right) {
        if (right == null) {
            return left;
        }
        return left == null ? right : left.add(right);
    }

    public record IncomingLine(String title, String change, BigDecimal quantity, BigDecimal clientPrice) {
    }

    public record VersionView(
            Long id,
            int version,
            String date,
            String author,
            String reason,
            BigDecimal amount,
            boolean approved,
            String addendum,
            String act,
            BigDecimal invoice,
            List<LineView> lines
    ) {
    }

    public record LineView(
            String title,
            String change,
            BigDecimal quantity,
            BigDecimal plan,
            BigDecimal fact,
            BigDecimal clientPrice,
            BigDecimal cost,
            BigDecimal margin
    ) {
    }

    public record CompareView(AmountChange amount, BigDecimal difference, List<CompareLine> lines) {
    }

    public record AmountChange(BigDecimal was, BigDecimal now) {
    }

    public record CompareLine(String title, BigDecimal was, BigDecimal now) {
    }

    private record DraftLine(
            String title,
            String change,
            BigDecimal quantity,
            BigDecimal plan,
            BigDecimal fact,
            BigDecimal clientPrice,
            BigDecimal cost,
            BigDecimal margin,
            boolean removed
    ) {
    }
}
