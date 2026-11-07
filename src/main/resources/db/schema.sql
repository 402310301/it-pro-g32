/*
 Intelligent Surplus Food Redistribution System - MySQL 8 schema.
 The application runs this script automatically on start-up.
 Every statement uses IF NOT EXISTS, so running it again is safe.
 You can also run it by hand in MySQL Workbench.
*/

CREATE TABLE IF NOT EXISTS organisations (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  name              VARCHAR(200) NOT NULL UNIQUE,
  role              VARCHAR(20)  NOT NULL,
  organisation_type VARCHAR(60)  NOT NULL,
  area              VARCHAR(80)  NOT NULL,
  address           VARCHAR(255) NOT NULL,
  latitude          DECIMAL(9,6) NOT NULL,
  longitude         DECIMAL(9,6) NOT NULL,
  contact_name      VARCHAR(120),
  approval_status   VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',
  created_at        DATETIME     NOT NULL,
  CONSTRAINT chk_org_role   CHECK (role IN ('DONOR', 'RECIPIENT')),
  CONSTRAINT chk_org_status CHECK (approval_status IN ('PENDING', 'APPROVED'))
);

CREATE TABLE IF NOT EXISTS transporters (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(120)  NOT NULL,
  phone        VARCHAR(40),
  vehicle      VARCHAR(120)  NOT NULL,
  capacity_kg  DECIMAL(10,2) NOT NULL,
  refrigerated BOOLEAN       NOT NULL DEFAULT FALSE,
  active       BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at   DATETIME      NOT NULL,
  CONSTRAINT chk_transporter_capacity CHECK (capacity_kg > 0)
);

CREATE TABLE IF NOT EXISTS food_donations (
  id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
  reference                 VARCHAR(40)   NOT NULL UNIQUE,
  donor_organisation_id     BIGINT        NOT NULL,
  category                  VARCHAR(80)   NOT NULL,
  description               TEXT          NOT NULL,
  quantity                  DECIMAL(12,2) NOT NULL,
  unit                      VARCHAR(30)   NOT NULL,
  storage_requirement       VARCHAR(40)   NOT NULL,
  expiry_at                 DATETIME      NOT NULL,
  collection_deadline       DATETIME      NOT NULL,
  area                      VARCHAR(80)   NOT NULL,
  address                   VARCHAR(255)  NOT NULL,
  latitude                  DECIMAL(9,6)  NOT NULL,
  longitude                 DECIMAL(9,6)  NOT NULL,
  transport_required        BOOLEAN       NOT NULL DEFAULT FALSE,
  status                    VARCHAR(40)   NOT NULL DEFAULT 'Listed',
  recipient_organisation_id BIGINT        NULL,
  quantity_allocated        DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at                DATETIME      NOT NULL,
  updated_at                DATETIME      NOT NULL,
  CONSTRAINT fk_donation_donor     FOREIGN KEY (donor_organisation_id)     REFERENCES organisations(id),
  CONSTRAINT fk_donation_recipient FOREIGN KEY (recipient_organisation_id) REFERENCES organisations(id),
  CONSTRAINT chk_donation_quantity CHECK (quantity > 0),
  CONSTRAINT chk_donation_deadline CHECK (collection_deadline <= expiry_at)
);

CREATE TABLE IF NOT EXISTS recipient_needs (
  id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipient_organisation_id BIGINT        NOT NULL,
  category                  VARCHAR(80)   NOT NULL,
  description               VARCHAR(255)  NOT NULL,
  quantity_needed           DECIMAL(12,2) NOT NULL,
  max_per_donation          DECIMAL(12,2) NOT NULL,
  storage_capacity          DECIMAL(12,2) NOT NULL,
  storage_type              VARCHAR(40)   NOT NULL,
  opens_at                  VARCHAR(5)    NOT NULL,
  closes_at                 VARCHAR(5)    NOT NULL,
  urgency                   VARCHAR(20)   NOT NULL,
  active                    BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at                DATETIME      NOT NULL,
  CONSTRAINT fk_need_recipient FOREIGN KEY (recipient_organisation_id) REFERENCES organisations(id),
  CONSTRAINT chk_need_quantities CHECK (quantity_needed >= 0 AND max_per_donation > 0 AND storage_capacity >= 0),
  CONSTRAINT chk_need_urgency CHECK (urgency IN ('Critical', 'High', 'Medium', 'Low'))
);

CREATE TABLE IF NOT EXISTS matches (
  id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
  donation_id              BIGINT        NOT NULL,
  recipient_need_id        BIGINT        NOT NULL,
  rank_position            INT           NOT NULL,
  score                    DECIMAL(5,1)  NOT NULL,
  distance_km              DECIMAL(7,1)  NOT NULL,
  quantity_can_accept      DECIMAL(12,2) NOT NULL,
  expiry_urgency           DECIMAL(5,1)  NOT NULL,
  recipient_demand         DECIMAL(5,1)  NOT NULL,
  food_suitability         DECIMAL(5,1)  NOT NULL,
  storage_capacity         DECIMAL(5,1)  NOT NULL,
  quantity_compatibility   DECIMAL(5,1)  NOT NULL,
  distance_score           DECIMAL(5,1)  NOT NULL,
  transport_availability   DECIMAL(5,1)  NOT NULL,
  collection_compatibility DECIMAL(5,1)  NOT NULL,
  explanation              TEXT          NOT NULL,
  status                   VARCHAR(20)   NOT NULL DEFAULT 'RECOMMENDED',
  decline_reason           VARCHAR(255),
  created_at               DATETIME      NOT NULL,
  CONSTRAINT fk_match_donation FOREIGN KEY (donation_id)       REFERENCES food_donations(id),
  CONSTRAINT fk_match_need     FOREIGN KEY (recipient_need_id) REFERENCES recipient_needs(id),
  CONSTRAINT uq_match UNIQUE (donation_id, recipient_need_id),
  CONSTRAINT chk_match_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT chk_match_status CHECK (status IN ('RECOMMENDED', 'ACCEPTED', 'DECLINED', 'NOT_SELECTED'))
);

CREATE TABLE IF NOT EXISTS transport_assignments (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  donation_id        BIGINT        NOT NULL,
  transporter_id     BIGINT        NOT NULL,
  pickup_window      VARCHAR(80)   NOT NULL,
  status             VARCHAR(20)   NOT NULL DEFAULT 'OFFERED',
  created_at         DATETIME      NOT NULL,
  updated_at         DATETIME      NOT NULL,
  CONSTRAINT fk_assignment_donation    FOREIGN KEY (donation_id)    REFERENCES food_donations(id),
  CONSTRAINT fk_assignment_transporter FOREIGN KEY (transporter_id) REFERENCES transporters(id),
  CONSTRAINT chk_assignment_status CHECK (status IN ('OFFERED', 'ACCEPTED', 'PICKED_UP', 'DELIVERED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS notifications (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  donation_id       BIGINT       NULL,
  title             VARCHAR(200) NOT NULL,
  message           TEXT         NOT NULL,
  notification_type VARCHAR(40)  NOT NULL,
  is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        DATETIME     NOT NULL,
  CONSTRAINT fk_notification_donation FOREIGN KEY (donation_id) REFERENCES food_donations(id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor           VARCHAR(120) NOT NULL,
  action          VARCHAR(120) NOT NULL,
  record_type     VARCHAR(60)  NOT NULL,
  record_id       BIGINT       NOT NULL,
  previous_status VARCHAR(40),
  new_status      VARCHAR(40),
  created_at      DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS matching_configuration (
  id                       INT PRIMARY KEY,
  expiry_urgency           INT NOT NULL,
  recipient_demand         INT NOT NULL,
  food_suitability         INT NOT NULL,
  storage_capacity         INT NOT NULL,
  quantity_compatibility   INT NOT NULL,
  distance                 INT NOT NULL,
  transport_availability   INT NOT NULL,
  collection_compatibility INT NOT NULL,
  CONSTRAINT chk_config_single_row CHECK (id = 1),
  CONSTRAINT chk_config_total CHECK (expiry_urgency + recipient_demand + food_suitability + storage_capacity
    + quantity_compatibility + distance + transport_availability + collection_compatibility = 100)
);

-- People who can sign in. Passwords are stored as salted PBKDF2 hashes, never as plain text.
-- DONOR and RECIPIENT users belong to an organisation; ADMIN and COORDINATOR users do not.
CREATE TABLE IF NOT EXISTS users (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(60)  NOT NULL UNIQUE,
  full_name       VARCHAR(120) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  role            VARCHAR(20)  NOT NULL,
  organisation_id BIGINT       NULL,
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  last_login_at   DATETIME     NULL,
  created_at      DATETIME     NOT NULL,
  CONSTRAINT fk_user_organisation FOREIGN KEY (organisation_id) REFERENCES organisations(id),
  CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'COORDINATOR', 'DONOR', 'RECIPIENT'))
);
