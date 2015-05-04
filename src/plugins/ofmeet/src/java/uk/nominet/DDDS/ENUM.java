/*
 * Copyright 2009 Nominet UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.nominet.DDDS;

import java.util.List;

import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.Name;

/**
 * An implementation of an ENUM (<a href="http://tools.ietf.org/html/rfc3761">RFC 3761</a>)
 * database lookup system, written as a concrete implementation of {@link DDDSinDNS}
 *
 * @author Ray Bellis
 * @see DDDSinDNS
 */
public class ENUM extends DDDSinDNS {

	private String suffix = null;

	/**
	 *
	 * @param suffix The DNS domain name suffix to be appended to all ENUM lookups in the DNS
	 */
	public ENUM(String suffix) {
		this.suffix = suffix;
	}

	/**
	 * Constructs an ENUM object with a default suffix of "e164.arpa".
	 */
	public ENUM() {
		this("e164.arpa");
	}

	/**
	 * Converts the supplied string into ENUM's application unique
	 * string format, that is an E.164 number prefixed with a '+'
	 * and with any other non-digit characters removed.
	 *
	 * See 2.1 of <a href="http://tools.ietf.org/html/rfc3761">RFC 3761</a>
	 *
	 * @param input the number being looked up
	 * @return      the number in AUS format
	 * @see uk.nominet.DDDS.DDDS#convertToAUS(java.lang.String)
	 */
	protected String convertKeyToAUS(String input) {
		char[] ca = input.toCharArray();
		int len = ca.length;

		StringBuffer sb = new StringBuffer(len);
		sb.append('+');
		for (int i = 0; i < len; ++i) {
			char c = ca[i];
			if (Character.isDigit(c)) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * <p>Converts an input value (in Application Unique String format) into the
	 * database key (in this case a domain name).</p>
	 *
	 * <p>The ENUM key format is the AUS (with the leading '+' removed) in reverse
	 * order, with each number treated as a separate label.  The current suffix
	 * is then appended, eg.:</p>
	 *
	 * <code>+441865332211</code> becomes
	 *   <code>1.1.2.2.3.3.5.6.8.1.4.4.e164.arpa.</code>
	 *
	 * @param key The Application Unique String
	 * @return    The AUS converted to ENUM database format (e.g. a domain name)
	 * @see uk.nominet.DDDS.DDDS#convertToDatabaseKey(java.lang.String)
	 */
	protected String convertAUSToDBKey(String key) {

		char[] ca = key.toCharArray();
		int len = ca.length;

		StringBuffer sb = new StringBuffer(len * 2 + suffix.length());
		for (int i = len - 1; i > 0; --i) {
			sb.append(ca[i]);
			sb.append('.');
		}
		sb.append(suffix);

		return sb.toString();
	}

	/**
	 * @see uk.nominet.DDDS.DDDSinDNS#parseAndAddRule(java.util.List, java.lang.String, org.xbill.DNS.NAPTRRecord)
	 */
	@Override
	protected void parseAndAddRule(String aus, NAPTRRecord naptr,
			List<Rule> rules)
	{
        // split service field on '+' token
		String service = unescape(naptr.getService());
        String[] services = service.toLowerCase().split("\\+");

        // check that resulting fields are valid
        if (services.length < 2) return;        // not x+y
        if (!services[0].equals("e2u")) return; // not E2U+...

        for (int i = 1; i < services.length; ++i) {
			EnumRule rule = new EnumRule(aus,
					naptr.getOrder(),
					naptr.getPreference(),
					unescape(naptr.getFlags()),
					"E2U+" + services[i],
					unescape(naptr.getRegexp()),
					naptr.getReplacement(),
					i);
			rules.add(rule);
        }
	}

	public class EnumRule extends DNSRule {

		protected int rank;

		public EnumRule(String aus, int order, int preference, String flags,
				String service, String regexp, Name replacement, int rank)
		{
			super(aus, order, preference, flags, service, regexp, replacement);
			this.rank = rank;
		}

		public boolean isTerminal() {
			return flags.toLowerCase().equals("u");
		}

		/**
		 * Extends the {@link DNSRule#compareTo()} method by also looking at the {@link rank}
		 * field.  This field is used to remember the order of entries when the ENUM Service
		 * field is of the form E2U+s1+s2+...
		 *
		 * @see uk.nominet.DDDS.DDDSinDNS.DNSRule#compareTo(uk.nominet.DDDS.DDDSinDNS.DNSRule)
		 */
		@Override
		public int compareTo(DNSRule r) {
			int n = super.compareTo(r);
			if (n == 0 && r instanceof EnumRule) {
				n = rank - ((EnumRule)r).rank;
			}
			return n;
		}
	}
}
