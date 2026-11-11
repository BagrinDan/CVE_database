CREATE TABLE cve (
    id          TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    severity    TEXT NOT NULL,          
    cvss_score  NUMERIC(3,1),
    published   DATE,
    version     BIGINT NOT NULL DEFAULT 0   
);
 
INSERT INTO cve (id, description, severity, cvss_score, published) VALUES
    ('CVE-2021-44228', 'Log4Shell: remote code execution in Apache Log4j 2 via JNDI lookups', 'CRITICAL', 10.0, '2021-12-10'),
    ('CVE-2014-0160',  'Heartbleed: OpenSSL leaks process memory through the TLS heartbeat extension', 'HIGH', 7.5, '2014-04-07'),
    ('CVE-2017-0144',  'EternalBlue: remote code execution in Microsoft SMBv1 server', 'HIGH', 8.1, '2017-03-16');
