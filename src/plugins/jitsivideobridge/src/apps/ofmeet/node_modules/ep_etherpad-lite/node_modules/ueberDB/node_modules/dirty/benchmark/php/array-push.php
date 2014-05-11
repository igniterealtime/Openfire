<?php
ini_set('memory_limit', '256M');

$COUNT = 1e6;
$a = array();

$start = microtime(true);
for ($i = 0; $i < $COUNT; $i++) {
  array_push($a, $i);
}

$ms = (microtime(true) - $start) * 1000;
$mhz = ((($COUNT / ($ms / 1000)) / 1e6));
$million = $COUNT / 1e6;

echo sprintf("%.2f Mhz (%d million in %d ms)\n", $mhz, $million, $ms);
