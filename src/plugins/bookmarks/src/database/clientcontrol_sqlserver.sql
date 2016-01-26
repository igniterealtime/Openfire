/* $Revision$   */
/* $Date$       */

INSERT INTO ofVersion (name, version) VALUES ('clientcontrol', 0);

CREATE TABLE ofBookmark (
   bookmarkID       BIGINT           NOT NULL,
   bookmarkType     VARCHAR(50)      NOT NULL,
   bookmarkName     NVARCHAR(255)    NOT NULL,
   bookmarkValue    NVARCHAR(1024)   NOT NULL,
   isGlobal         INT              NOT NULL,
   CONSTRAINT ofBookmark_pk PRIMARY KEY (bookmarkID)
);

CREATE TABLE ofBookmarkPerm (
   bookmarkID   BIGINT               NOT NULL,
   bookmarkType TINYINT              NOT NULL,
   name         NVARCHAR(255)        NOT NULL,
   CONSTRAINT ofBookmarkPerm_pk PRIMARY KEY(bookmarkID, name, bookmarkType)
);

CREATE TABLE ofBookmarkProp (
   bookmarkID   BIGINT               NOT NULL,
   name         NVARCHAR(100)        NOT NULL,
   propValue    NTEXT                NOT NULL,
   CONSTRAINT ofBookmarkProp_pk PRIMARY KEY (bookmarkID, name)
);
