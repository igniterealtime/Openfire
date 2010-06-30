/* Ajax is hard :( */

function HideField(fieldName) {
   $(fieldName).hide();
}

function ShowSourceField(decidingElement) {
   if ($(decidingElement).value.toString() == "Other") {
        $("SourceOther").show();
   }
   else {
         $("SourceOther").hide();
   }

   if ($(decidingElement).value.toString() == "Group") {
        $("SourceGroup").show();
   }
   else {
         $("SourceGroup").hide();
   }

   if ($(decidingElement).value.toString() == "User") {
        $("SourceUser").show();
   }
   else {
         $("SourceUser").hide();
   }
   if ($(decidingElement).value.toString() == "Component") {
        $("SourceComponent").show();
   }
   else {
         $("SourceComponent").hide();
   }
}

function ShowDestinationField(decidingElement) {
   if ($(decidingElement).value.toString() == "Other") {
        $("DestOther").show();
   }
   else {
         $("DestOther").hide();
   }

   if ($(decidingElement).value.toString() == "Group") {
        $("DestGroup").show();
   }
   else {
         $("DestGroup").hide();
   }

   if ($(decidingElement).value.toString() == "User") {
        $("DestUser").show();
   }
   else {
         $("DestUser").hide();
   }
   if ($(decidingElement).value.toString() == "Component") {
        $("DestComponent").show();
   }
   else {
         $("DestComponent").hide();
   }
}

function ShowExtraOptions(decidingElement) {
   var value =  $(decidingElement).value.toString();
   if ($(decidingElement).value.toString() =="Redirect") {
        $("RedirectOptions").show();
   }
   else {
       $("RedirectOptions").hide();
   }
}