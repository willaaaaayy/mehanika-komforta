alter table client_objects add column object_type varchar(80);
alter table client_objects add column area_m2 numeric(12, 2);
alter table client_objects add column assigned_to varchar(80);

update client_objects set status = 'prep' where status = 'подготовка';
