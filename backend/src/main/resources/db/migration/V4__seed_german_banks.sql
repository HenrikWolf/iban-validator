-- Large German banks (main BLZ each), so the internal validator can resolve bank data for them.
INSERT INTO bank (bic, bank_name, country_name, bank_code) VALUES
    ('DEUTDEFFXXX', 'Deutsche Bank', 'Germany', '50070010'),
    ('COBADEFFXXX', 'Commerzbank', 'Germany', '50040000'),
    ('PBNKDEFFXXX', 'Postbank', 'Germany', '10010010'),
    ('HYVEDEMMXXX', 'UniCredit Bank (HypoVereinsbank)', 'Germany', '70020270'),
    ('GENODEFFXXX', 'DZ Bank', 'Germany', '50060400'),
    ('INGDDEFFXXX', 'ING-DiBa', 'Germany', '50010517'),
    ('BYLADEM1001', 'Deutsche Kreditbank (DKB)', 'Germany', '12030000'),
    ('CMCIDEDDXXX', 'TARGOBANK', 'Germany', '30020900'),
    ('COBADEHDXXX', 'comdirect bank', 'Germany', '20041111'),
    ('NTSBDEB1XXX', 'N26 Bank', 'Germany', '10011001'),
    ('BELADEBEXXX', 'Berliner Sparkasse', 'Germany', '10050000'),
    ('HASPDEHHXXX', 'Hamburger Sparkasse', 'Germany', '20050550');