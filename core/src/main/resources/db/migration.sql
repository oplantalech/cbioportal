##version: 1.0.0
CREATE TABLE info (DB_SCHEMA_VERSION VARCHAR(8));
INSERT INTO info VALUES ("1.0.0");

##version: 1.1.0
CREATE TABLE sample_list LIKE patient_list;
INSERT sample_list SELECT * FROM patient_list;
CREATE TABLE sample_list_list LIKE patient_list_list;
INSERT sample_list_list SELECT * FROM patient_list_list;
ALTER TABLE sample_list_list CHANGE PATIENT_ID SAMPLE_ID INT(11);
UPDATE info SET DB_SCHEMA_VERSION="1.1.0";

##version: 1.2.0
ALTER TABLE cna_event AUTO_INCREMENT=1;
ALTER TABLE mutation add UNIQUE KEY `UQ_MUTATION_EVENT_ID_GENETIC_PROFILE_ID_SAMPLE_ID` (`MUTATION_EVENT_ID`,`GENETIC_PROFILE_ID`,`SAMPLE_ID`);
ALTER TABLE sample_profile add UNIQUE KEY `UQ_SAMPLE_ID_GENETIC_PROFILE_ID` (`SAMPLE_ID`,`GENETIC_PROFILE_ID`);
UPDATE info SET DB_SCHEMA_VERSION="1.2.0";

##version: 1.2.1
SET @s = (SELECT IF(    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_name = 'cancer_study' AND table_schema = DATABASE() AND column_name = 'STATUS') > 0,  "SELECT 1", " ALTER TABLE cancer_study ADD STATUS int(1) DEFAULT NULL"));
PREPARE stmt FROM @s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
UPDATE info SET DB_SCHEMA_VERSION="1.2.1";

##version: 1.2.2

ALTER TABLE gene ADD COLUMN `PREVIOUS_SYMBOL` VARCHAR(255) NULL;
UPDATE info SET DB_SCHEMA_VERSION="1.2.2";