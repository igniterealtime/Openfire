# $Revision:  $
# $Date:  $

# Increase size of column digest_frequency in pubsubSubscription
ALTER TABLE pubsubSubscription MODIFY digest_frequency INT NOT NULL;

UPDATE jiveVersion set version=9 where name = 'wildfire';
