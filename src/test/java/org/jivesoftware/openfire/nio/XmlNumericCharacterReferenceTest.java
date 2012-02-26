package org.jivesoftware.openfire.nio;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * This unit tests verifies the correct detection of numeric character references that have invalid numeric values.
 * 
 * From the XML 1.0 specificaton: <quote><b>Character Reference</b> <code><pre>CharRef	   ::=   	'&#' [0-9]+ ';'
 * | '&#x' [0-9a-fA-F]+ ';'	[WFC: Legal Character]</pre></code>
 * <p/>
 * <b>Well-formedness constraint: Legal Character<b> Characters referred to using character references must match the
 * production for Char.
 * 
 * (...)
 * <code><pre>Char	   ::=   	#x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]</pre></code> any
 * Unicode character, excluding the surrogate blocks, FFFE, and FFFF. <quote>
 * 
 * This test is based on three large sets of values.
 * <ol>
 * <li>A set containing only illegal numeric character values only;</li>
 * <li>A set containing legal numeric character reference values only;</li>
 * <li>A set containing values that are no character references at all (but do resemble them)</li>
 * </ol>
 * 
 * The first and second set consist of lines in which each line contains both the decimal as well as the hexidecimal
 * representation of the same numeric character reference. The remainder of the line is filled with zero-padded copies
 * of the same values. The various values (on each new line) are picked from the edges of each of the ranges that make
 * up the complete set of valid characters.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * 
 * @see http://www.w3.org/TR/2008/REC-xml-20081126/#dt-charref
 */
public class XmlNumericCharacterReferenceTest {

	final String[] illegalNumericCharacterReferences = new String[] {
		"&#0;" , "&#x0;", "&#00;" , "&#x00;", "&#000;" , "&#x000;", "&#0000;" , "&#x0000;", "&#00000;" , "&#x00000;", "&#000000;" , "&#x000000;",  
		"&#1;" , "&#x1;", "&#01;" , "&#x01;", "&#001;" , "&#x001;", "&#0001;" , "&#x0001;", "&#00001;" , "&#x00001;", "&#000001;" , "&#x000001;",
		"&#2;" , "&#x2;", "&#02;" , "&#x02;", "&#002;" , "&#x002;", "&#0002;" , "&#x0002;", "&#00002;" , "&#x00002;", "&#000002;" , "&#x000002;",
		"&#3;" , "&#x3;", "&#03;" , "&#x03;", "&#003;" , "&#x003;", "&#0003;" , "&#x0003;", "&#00003;" , "&#x00003;", "&#000003;" , "&#x000003;",
		"&#4;" , "&#x4;", "&#04;" , "&#x04;", "&#004;" , "&#x004;", "&#0004;" , "&#x0004;", "&#00004;" , "&#x00004;", "&#000004;" , "&#x000004;",
		"&#5;" , "&#x5;", "&#05;" , "&#x05;", "&#005;" , "&#x005;", "&#0005;" , "&#x0005;", "&#00005;" , "&#x00005;", "&#000005;" , "&#x000005;",
		"&#6;" , "&#x6;", "&#06;" , "&#x06;", "&#006;" , "&#x006;", "&#0006;" , "&#x0006;", "&#00006;" , "&#x00006;", "&#000006;" , "&#x000006;",
		"&#7;" , "&#x7;", "&#07;" , "&#x07;", "&#007;" , "&#x007;", "&#0007;" , "&#x0007;", "&#00007;" , "&#x00007;", "&#000007;" , "&#x000007;",
		"&#8;" , "&#x8;", "&#08;" , "&#x08;", "&#008;" , "&#x008;", "&#0008;" , "&#x0008;", "&#00008;" , "&#x00008;", "&#000008;" , "&#x000008;",
		"&#11;", "&#xB;", "&#011;", "&#x0B;", "&#0011;", "&#x00B;", "&#00011;", "&#x000B;", "&#000011;", "&#x0000B;", "&#0000011;", "&#x00000B;",
		"&#12;", "&#xC;", "&#012;", "&#x0C;", "&#0012;", "&#x00C;", "&#00012;", "&#x000C;", "&#000012;", "&#x0000C;", "&#0000012;", "&#x00000C;",
		"&#14;", "&#xE;", "&#014;", "&#x0E;", "&#0014;", "&#x00E;", "&#00014;", "&#x000E;", "&#000014;", "&#x0000E;", "&#0000014;", "&#x00000E;",
		"&#15;", "&#xF;", "&#015;", "&#x0F;", "&#0015;", "&#x00F;", "&#00015;", "&#x000F;", "&#000015;", "&#x0000F;", "&#0000015;", "&#x00000F;",
		
		"&#31;", "&#x1F;", "&#031;", "&#x01F;", "&#0031;", "&#x001F;", "&#00031;", "&#x0001F;", "&#000031;", "&#x00001F;", "&#0000031;", "&#x000001F;", 

		"&#55296;", "&#xD800;", "&#055296;", "&#x0D800;", "&#0055296;", "&#x00D800;", "&#00055296;", "&#x000D800;", "&#000055296;", "&#x0000D800;", 
		
		"&#57343;", "&#xDFFF;", "&#057343;", "&#x0DFFF;", "&#0057343;", "&#x00DFFF;", "&#00057343;", "&#x000DFFF;", "&#000057343;", "&#x0000DFFF;", 

		"&#65534;", "&#xFFFE;", "&#065534;", "&#x0FFFE;", "&#0065534;", "&#x00FFFE;", "&#00065534;", "&#x000FFFE;", "&#000065534;", "&#x0000FFFE;", 
		"&#65535;", "&#xFFFF;", "&#065535;", "&#x0FFFF;", "&#0065535;", "&#x00FFFF;", "&#00065535;", "&#x000FFFF;", "&#000065535;", "&#x0000FFFF;", 

		"&#1114112;", "&#x110000;", "&#01114112;", "&#x0110000;", "&#001114112;", "&#x00110000;", "&#0001114112;", "&#x000110000;"
	};
	
	final String[] legalNumericCharacterReferences = new String[] {
		"&#9;" , "&#x9;", "&#09;" , "&#x09;", "&#009;" , "&#x009;", "&#0009;" , "&#x0009;", "&#00009;" , "&#x00009;", "&#000009;" , "&#x000009;", 
		"&#10;", "&#xA;", "&#010;", "&#x0A;", "&#0010;", "&#x00A;", "&#00010;", "&#x000A;", "&#000010;", "&#x0000A;", "&#0000010;", "&#x00000A;",
		"&#13;", "&#xD;", "&#013;", "&#x0D;", "&#0013;", "&#x00D;", "&#00013;", "&#x000D;", "&#000013;", "&#x0000D;", "&#0000013;", "&#x00000D;",
		
		"&#32;", "&#x20;", "&#032;", "&#x020;", "&#0032;", "&#x0020;", "&#00032;", "&#x00020;", "&#000032;", "&#x000020;", "&#0000032;", "&#x0000020;", 
		"&#33;", "&#x21;", "&#033;", "&#x021;", "&#0033;", "&#x0021;", "&#00033;", "&#x00021;", "&#000033;", "&#x000021;", "&#0000033;", "&#x0000021;", 
		
		"&#55294;", "&#xD7FE;", "&#055294;", "&#x0D7FE;", "&#0055294;", "&#x00D7FE;", "&#00055294;", "&#x000D7FE;", "&#000055294;", "&#x0000D7FE;", 
		"&#55295;", "&#xD7FF;", "&#055295;", "&#x0D7FF;", "&#0055295;", "&#x00D7FF;", "&#00055295;", "&#x000D7FF;", "&#000055295;", "&#x0000D7FF;", 
		
		"&#57344;", "&#xE000", "&#057344;", "&#x0E000", "&#0057344;", "&#x00E000", "&#00057344;", "&#x000E000", "&#000057344;", "&#x0000E000", 
		"&#57345;", "&#xE001", "&#057345;", "&#x0E001", "&#0057345;", "&#x00E001", "&#00057345;", "&#x000E001", "&#000057345;", "&#x0000E001", 
		
		"&#65532;", "&#xFFFC;", "&#065532;", "&#x0FFFC;", "&#0065532;", "&#x00FFFC;", "&#00065532;", "&#x000FFFC;", "&#000065532;", "&#x0000FFFC;", 
		"&#65533;", "&#xFFFD;", "&#065533;", "&#x0FFFD;", "&#0065533;", "&#x00FFFD;", "&#00065533;", "&#x000FFFD;", "&#000065533;", "&#x0000FFFD;", 

		"&#65536;", "&#x10000;", "&#065536;", "&#x010000;", "&#0065536;", "&#x0010000;", "&#0000065536;", "&#x0000010000;", 
		"&#65537;", "&#x10001;", "&#065537;", "&#x010001;", "&#0065537;", "&#x0010001;", "&#0000065537;", "&#x0000010001;", 
		
		"&#1114110;", "&#x10FFFE;", "&#01114110;", "&#x010FFFE;", "&#00001114110;", "&#x000010FFFE;", "&#000001114110;", "&#x0000010FFFE;", 
		"&#1114111;", "&#x10FFFF;", "&#01114111;", "&#x010FFFF;", "&#00001114111;", "&#x000010FFFF;", "&#000001114111;", "&#x0000010FFFF;" 
	};
	
	final String[] notNumericCharacterRefrences = new String[] {
		"&amp;", 
		"&#9", "&#x9", "#9;", "#x9;", "&#1", "&#x1", "#1;", "#x1;",
		"&amp;#9;", "&amp;#x9;", "&amp;#1;", "&amp;#x1;" 
	};

	/**
	 * Iterates of the collection of legal numeric character references and asserts that
	 * {@link XMLLightweightParser#hasIllegalCharacterReferences(String)} does not find an illegal reference when each
	 * of these values are passed as an argument to this method.
	 */
	@Test
	public void testLegalNumericCharacterReferences() throws Exception {
		for(final String reference : legalNumericCharacterReferences) {
			// do magic
			final boolean result =  XMLLightweightParser.hasIllegalCharacterReferences(reference);
			
			// verify
			assertFalse("Value \""+reference+"\" is reported to contain an illegal numeric character reference, even though this hard-coded test value should not contain one.", result);
		}
	}

	/**
	 * Iterates of the collection of illegal numeric character references and asserts that
	 * {@link XMLLightweightParser#hasIllegalCharacterReferences(String)} finds an illegal reference for each of these
	 * values passed as an argument to this method.
	 */
	@Test
	public void testIllegalNumericCharacterReferences() throws Exception {
		for(final String reference : illegalNumericCharacterReferences) {
			// do magic
			final boolean result =  XMLLightweightParser.hasIllegalCharacterReferences(reference);
			
			// verify
			assertTrue("No illegal numeric character reference was found in value \""+reference+"\", even though this hard-coded test value should contain one.", result);
		}
	}

	/**
	 * Iterates of the collection of values that are not numeric character references and asserts that
	 * {@link XMLLightweightParser#hasIllegalCharacterReferences(String)} does not find an illegal reference when each
	 * of these values are passed as an argument to this method.
	 */
	@Test
	public void testnotNumericCharacterReferences() throws Exception {
		for(final String reference : legalNumericCharacterReferences) {
			// do magic
			final boolean result =  XMLLightweightParser.hasIllegalCharacterReferences(reference);
			
			// verify
			assertFalse("Value \""+reference+"\" is reported to contain an illegal numeric character reference, even though this hard-coded test value should not contain a numeric character reference at all.", result);
		}
	}

	/**
	 * Checks if {@link XMLLightweightParser#hasIllegalCharacterReferences(String)} correctly skips over a legal numeric
	 * reference if it is embedded in a snippet of text.
	 */
	@Test
	public void testTextWithLegalNumericCharacterReferences() throws Exception {
		// setup
		final String text = "The value &#x09; is a legal numeric character reference.";
		
		// do magic
		final boolean result = XMLLightweightParser.hasIllegalCharacterReferences(text);
		
		// verify
		assertFalse(result);
	}

	/**
	 * Checks if {@link XMLLightweightParser#hasIllegalCharacterReferences(String)} correctly identifies an illegal
	 * numeric reference if it is embedded in a snippet of text.
	 */
	@Test
	public void testTextWithIllegalNumericCharacterReferences() throws Exception {
		// setup
		final String text = "The value &#x01; is an illegal numeric character reference.";
		
		// do magic
		final boolean result = XMLLightweightParser.hasIllegalCharacterReferences(text);
		
		// verify
		assertTrue(result);
	}

	/**
	 * Checks if {@link XMLLightweightParser#hasIllegalCharacterReferences(String)} correctly identifies an illegal
	 * numeric reference if it is embedded in a snippet of text that also contains a legal reference.
	 */
	@Test
	public void testTextWithBothLegalAndIllegalNumericCharacterReferences() throws Exception {
		// setup
		final String text = "The value &#x09; is a legal numeric character reference, but the value &#x01; is not.";
		
		// do magic
		final boolean result = XMLLightweightParser.hasIllegalCharacterReferences(text);
		
		// verify
		assertTrue(result);
	}	
}
