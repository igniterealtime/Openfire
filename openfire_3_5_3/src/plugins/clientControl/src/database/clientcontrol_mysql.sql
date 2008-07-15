# $Revision$
# $Date$

INSERT INTO jiveVersion (name, version) VALUES ('clientcontrol', 0);

CREATE TABLE ofBookmark (
   bookmarkID       BIGINT            NOT NULL,
   bookmarkType     VARCHAR(50)       NOT NULL,
   bookmarkName     VARCHAR(255)      NOT NULL,
   bookmarkValue    VARCHAR(255)      NOT NULL,
   isGlobal         INT               NOT NULL,
   PRIMARY KEY (bookmarkID)
);

CREATE TABLE ofBookmarkPerm (
   bookmarkID   BIGINT                NOT NULL,
   bookmarkType TINYINT               NOT NULL,
   name         VARCHAR(255)          NOT NULL,
   PRIMARY KEY(bookmarkID, name, bookmarkType)
);

CREATE TABLE ofBookmarkProp (
   bookmarkID   BIGINT                NOT NULL,
   name         VARCHAR(100)          NOT NULL,
   propValue    TEXT                  NOT NULL,
   PRIMARY KEY  (bookmarkID, name)
);
