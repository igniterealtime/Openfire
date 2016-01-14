-- $Revision$
-- $Date$

INSERT INTO ofVersion (name, version) VALUES ('clientcontrol', 0);

CREATE TABLE ofBookmark (
   bookmarkID       INTEGER           NOT NULL,
   bookmarkType     VARCHAR2(50)      NOT NULL,
   bookmarkName     VARCHAR2(255)     NOT NULL,
   bookmarkValue    VARCHAR2(1024)    NOT NULL,
   isGlobal         INT               NOT NULL,
   CONSTRAINT ofBookmark_pk PRIMARY KEY (bookmarkID)
);

CREATE TABLE ofBookmarkPerm (
   bookmarkID   INTEGER              NOT NULL,
   bookmarkType NUMBER(2)            NOT NULL,
   name         VARCHAR2(255)        NOT NULL,
   CONSTRAINT ofBookmarkPerm_pk PRIMARY KEY(bookmarkID, name, bookmarkType)
);

CREATE TABLE ofBookmarkProp (
   bookmarkID   INTEGER               NOT NULL,
   name         VARCHAR2(100)         NOT NULL,
   propValue    LONG                  NOT NULL,
   CONSTRAINT ofBookmarkProp_pk PRIMARY KEY (bookmarkID, name)
);
