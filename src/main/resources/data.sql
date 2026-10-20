INSERT INTO customer (id, name, tenant_code) VALUES
 (1, 'Velmar Logistik', 'VLG'),
 (2, 'Klinikverbund Wendmoor', 'KWM'),
 (3, 'Tessberg Maschinenbau', 'TMB');
INSERT INTO learner (id, customer_id, name, email, group_name) VALUES
 (1, 1, 'Jonas Albrecht',  'j.albrecht@velmar.example',   'Disposition'),
 (2, 1, 'Mira Kowalski',   'm.kowalski@velmar.example',   'Disposition'),
 (3, 2, 'Deniz Yilmaz',    'd.yilmaz@wendmoor.example',    'Pflege'),
 (4, 2, 'Sam Richter',     's.richter@wendmoor.example',   'Verwaltung'),
 (5, 3, 'Alex Nguyen',     'a.nguyen@tessberg.example',   'Fertigung'),
 (6, 3, 'Lea Hoffmann',    'l.hoffmann@tessberg.example', 'Fertigung');
