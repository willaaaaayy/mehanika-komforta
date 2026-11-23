import json
import logging
import os
import urllib.error
import urllib.request

log = logging.getLogger(__name__)


def forward_lead(lead: dict) -> bool:
    url = os.getenv("CRM_URL", "").strip()
    token = os.getenv("LEAD_TOKEN", "").strip()
    if not url or not token:
        return False
    name = (lead.get("name") or "").strip()
    phone = (lead.get("phone") or "").strip()
    object_type = (lead.get("object_type") or "").strip()
    if not name or not phone or not object_type:
        return False
    body = json.dumps(
        {
            "name": name,
            "phone": phone,
            "objectType": object_type,
            "source": "telegram",
        },
        ensure_ascii=False,
    ).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Lead-Token": token,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=3) as response:
            return 200 <= response.status < 300
    except (urllib.error.URLError, TimeoutError, OSError):
        log.exception("crm forward failed")
        return False
