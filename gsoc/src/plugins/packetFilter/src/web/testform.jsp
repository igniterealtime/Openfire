<script type="text/javascript"
    src="/plugins/packetfilter/dwr2/interface/RuleManagerProxy.js"> </script>
<script type="text/javascript"
    src="/plugins/packetfilter/dwr2/engine.js"> </script>

<script language="JavaScript" type="text/javascript">
function handleGetData(str) {
  alert(str);
}
</script>

<body onload="RuleManagerProxy.getRuleById(4, handleGetData);">


</body>
