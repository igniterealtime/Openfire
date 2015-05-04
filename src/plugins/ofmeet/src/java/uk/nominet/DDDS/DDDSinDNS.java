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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.NAPTRRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Type;

public abstract class DDDSinDNS extends DDDS {

	public List<Rule> lookupRules(String aus, String key) {

		ArrayList<Rule> rules = new ArrayList<Rule>();

		try {
			Resolver resolver = new ExtendedResolver();
			resolver.setTimeout(2);
			Lookup lookup = new Lookup(key, Type.NAPTR);
			lookup.setResolver(resolver);
			Record[] records = lookup.run();

			if (records != null) {
				for (int i = 0; i < records.length; ++i) {
					// type check necessary in case of other RRtypes in the
					// Answer
					// Section
					if (records[i] instanceof NAPTRRecord) {
						parseAndAddRule(aus, (NAPTRRecord) records[i], rules);
					}
				}
			}
		} catch (Exception e) {
			/* do nothing */
		}
		return rules;
	}

	/**
	 * This method parses a {@link NAPTRRecord} and adds it to a list of rules
	 */
	protected abstract void parseAndAddRule(String aus, NAPTRRecord naptr, List<Rule> rules);

	/**
	 * dnsjava returns strings in presentation format, so real backslashes in
	 * the input are doubled. This removes them.
	 */
	protected final static String unescape(String input) {
		char[] c = input.toCharArray();
		StringBuffer sb = new StringBuffer(input.length());
		for (int i = 0; i < c.length; ++i) {
			if (c[i] == '\\') {
				// @todo - may fall off the end
				i++;
			}
			sb.append(c[i]);
		}
		return sb.toString();
	}

	/**
	 * DNSRule encapsulates the contents of a NAPTR record, as an abstract
	 * implementation of the DDDS {@link Rule} interface.
	 * 
	 * Full implementation requires creation of a concrete subclass.
	 * 
	 * @see Rule
	 */
	public abstract class DNSRule implements Rule, Comparable<DNSRule> {

		protected String aus;
		protected int order;
		protected int preference;
		protected String flags;
		protected String service;
		protected String regexp;
		protected Name replacement;

		/**
		 * Compares the current DNSRule object to another one. The comparison
		 * follows the standard DDDS rule, namely that rules are sorted first by
		 * "order", and then by "priority".
		 * 
		 * @param r
		 *            The rule to compare against
		 * @return negative, zero, or positive value
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(DNSRule r) {
			if (order != r.order) {
				return (order - r.order);
			} else {
				return (preference - r.preference);
			}
		}

		public int getOrder() {
			return order;
		}

		public int getPriority() {
			return preference;
		}

		public String getFlags() {
			return flags;
		}

		public String getService() {
			return service;
		}

		public DNSRule(String aus, int order, int preference, String flags,
				String service, String regexp, Name replacement) {
			this.aus = aus;
			this.preference = preference;
			this.flags = flags;
			this.service = service;
			this.regexp = regexp;
			this.replacement = replacement;
		}

		public DNSRule(String aus, NAPTRRecord naptr) {
			this.aus = aus;
			order = naptr.getOrder();
			preference = naptr.getPreference();
			flags = unescape(naptr.getFlags());
			service = unescape(naptr.getService());
			regexp = unescape(naptr.getRegexp());
			replacement = naptr.getReplacement();
		}

		public String evaluate() {

			String search = null;
			String replace = null;

			if (regexp == null || regexp.length() <= 0 || !isTerminal()) {
				return replacement.toString();
			}

			char[] c = regexp.toCharArray();
			char delim = c[0];

			// @todo - more sanity checking
			int i, j;
			for (i = 1; i < c.length; i++) {
				if (c[i] == '\\')
					continue;
				if (c[i] == delim) {
					search = regexp.substring(1, i++);
					break;
				}
			}

			for (j = i; j < c.length; ++j) {
				if (c[j] == '\\')
					continue;
				if (c[j] == delim) {
					replace = regexp.substring(i, j);
					break;
				}
			}

			// failed to parse - crap out here
			if (search == null || replace == null) {
				return null;
			}

			// convert \digit to $digit - Java regexps aren't the same
			// as the same as NAPTR records
			replace = replace.replaceAll("\\\\(\\d)", "\\$$1");

			// @todo - support case insensitive flag
			Pattern p = Pattern.compile(search);
			Matcher m = p.matcher(aus);
			if (m.matches()) {
				return m.replaceFirst(replace);
			} else {
				return null;
			}
		}

		public String toString() {
			return service + " " + evaluate();
		}

		public abstract boolean isTerminal();
	}
}
