/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.voip.server;

public class PhoneNumberPrefix {
        
    private PhoneNumberPrefix() {
    }

    private static String[] locations = new String[] {
        /* Canada */
        "CLG",
        "EDM",
        "HFX",
        "ISO",
        "MRK",
        "MRL",
        "NBR",
        "NOV",
        "OTW",
        "STF",
        "TNT",
        "VIC",
        "VNC",
        "WPG",
        /* United States */
        "ABQ",
        "ACT",
        "ARL",
        "ASE",
        "ATL",
        "AUS",
        "BES",
        "BHM",
        "BLM",
        "BLV",
        "BOI",
        "BOS",
        "BRM",
        "BRT",
        "BUR",
        "BWI",
        "CAM",
        "CHA",
        "CHE",
        "CHI",
        "CIN",
        "CLT",
        "CMH",
        "CMI",
        "COS",
        "CRY",
        "CUP",
        "DAY",
        "DFW",
        "DOW",
        "DSM",
        "DWT",
        "EGO",
        "FLL",
        "FMT",
        "FPT",
        "HAR",
        "HKS",
        "HMP",
        "HNL",
        "HOU",
        "HRN",
        "HRT",
        "HSV",
        "IDP",
        "IND",
        "IRV",
        "ITA",
        "JAX",
        "KOP",
        "LAM",
        "LAS",
        "LAX",
        "LSV",
        "MAR",
        "MBS",
        "MEM",
        "MIL",
        "MKE",
        "MLB",
        "MOB",
        "MPK",
        "MTL",
        "MTV",
        "NGW",
        "NPV",
        "NSH",
        "NWK",
        "NYC",
        "OMA",
        "ONT",
        "ORL",
        "OVP",
        "PAL",
        "PHX",
        "PIT",
        "PLN",
        "PLT",
        "PMB",
        "PRD",
        "RAN",
        "RGL",
        "RIC",
        "RKV",
        "RSN",
        "SAC",
        "SAN",
        "SAT",
        "SCA",
        "SCO",
        "SCR",
        "SFL",
        "SFO",
        "SHM",
        "SJC",
        "SLB",
        "SLC",
        "SMF",
        "SMT",
        "STL",
        "SUN",
        "TAL",
        "TIM",
        "TPA",
        "TUL",
        "TUS",
        "UNC",
        "UNI",
        "VNN",
        "WAS",
        "WCV",
        "WES",
        "WHT",
        "WIC",
        "WYZ",
        /* Argentina */
        "EZE",
        /* Brazil */
        "BHZ",
        "BRA",
        "RIO",
        "SAO",
        /* Chile */
        "SCH",
        /* Comlombia */
        "BOG",
        /* Mexico */
        "MEX",
        "MTY",
        /* Venezuela */
        "CCS" 
    };

    public static String getPrefix(String location) {
	if (location == null) {
	    return "";
	}

	if (isInAmerica(Bridge.getBridgeLocation())) {
	    if (isInAmerica(location)) {
		return "";
	    } 
	    return "70";
	}
	
	if (!isInAmerica(location)) {
	    return "";
	}

	return "70";
    }

    private static boolean isInAmerica(String location) {
	for (int i = 0; i < locations.length; i++) {
	    if (locations[i].equalsIgnoreCase(location)) {
		return true;
	    }
	}
	return false;
    }

    public static void main(String args[]) {
	if (args.length == 0) {
	    System.out.println(
		"usage:  java PhonePrefix <location> <location> ...");
	    System.exit(1);
	}

	for (int i = 0; i < args.length; i++) {
	    String prefix = getPrefix(args[i]);

	    if (prefix.equals("")) {
	        System.out.println(
		   "no prefix needed for location " + args[i]);
	    } else {
	        System.out.println(
		    "prefix " + prefix + " needed for location " + args[i]);
	    }
	}
    }

}
