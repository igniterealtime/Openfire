TRUNCATE TABLE ofBookmark;
INSERT INTO ofBookmark
(bookmarkID, bookmarkType, bookmarkName, bookmarkValue, isGlobal)
SELECT bookmarkID, bookmarkType, bookmarkName, bookmarkValue, isGlobal
FROM entBookmark;

TRUNCATE TABLE ofBookmarkPerm;
INSERT INTO ofBookmarkPerm
(bookmarkID, bookmarkType, name)
SELECT bookmarkID, bookmarkType, name
FROM entBookmarkPerm;

TRUNCATE TABLE ofBookmarkProp;
INSERT INTO ofBookmarkProp
(bookmarkID, name, propValue)
SELECT bookmarkID, name, propValue
FROM entBookmarkProp;

commit;
