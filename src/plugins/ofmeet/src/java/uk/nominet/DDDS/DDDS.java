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
import java.util.Arrays;
import java.util.List;

/**
 * This is an abstract implementation of the Dynamic Delegation Discovery
 * System (DDDS) as described in <a href="http://tools.ietf.org/html/rfc3401">RFC 3401</a>
 * and <a href="http://tools.ietf.org/html/rfc3402">RFC 3402</a>.
 * 
 * @author Ray Bellis
 * 
 */
public abstract class DDDS {

	protected abstract String convertKeyToAUS(String key);
	protected abstract String convertAUSToDBKey(String key);
	protected abstract List<Rule> lookupRules(String aus, String key);

	/**
	 * Implements the DDDS "first well known rule"
	 * 
	 * The default first well known rule is an "identity" operation
	 * 
	 * @param key  the input value to the rule
	 * @return     the result of the first well known rule
	 */
	protected String applyFirstWellKnownRule(String key) {
		return key;
	}

	final private void internalLookup(String aus, String key, List<Rule> rules) {

		/* lookup all rules matching the supplied AUS */
		List<Rule> aRules = lookupRules(aus, key);
		if (aRules == null) {
			return;
		}

		/* and add them to the list */
		for (Rule rule: aRules) {
			if (rule.isTerminal()) {
				rules.add(rule);
			} else {
				/* recurse if necessary */
				internalLookup(aus, rule.evaluate(), rules);
			}
		}
	}


	/**
	 * <p>Implements a DDDS lookup.</p>
	 * 
	 * @param input  the input key, in generic format
	 * @return       a list of {@link Rule} objects 
	 */
	final public Rule[] lookup(String input) {
		
		String aus = convertKeyToAUS(input);
		String key = applyFirstWellKnownRule(aus);
		key = convertAUSToDBKey(key);
		
		ArrayList<Rule> rules = new ArrayList<Rule>();
		internalLookup(aus, key, rules);
		
		Rule[] res = rules.toArray(new Rule[0]);
		Arrays.sort(res);
		
		return res;
	}
}
