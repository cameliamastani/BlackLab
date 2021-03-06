package nl.inl.blacklab;

import java.io.File;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.Terms;

public class MockTerms extends Terms {

	String[] words;

	public MockTerms(String... words) {
		this.words = words;
	}

	@Override
	public int indexOf(String term) {
		for (int i = 0; i < numberOfTerms(); i++) {
			if (get(i).equals(term))
				return i;
		}
		throw new IllegalArgumentException("Unknown term '" + term + "'");
	}

	@Override
	public void indexOf(MutableIntSet results, String term, boolean caseSensitive, boolean diacSensitive) {
		for (int i = 0; i < numberOfTerms(); i++) {
			if (caseSensitive) {
				if (get(i).equals(term))
					results.add(i);
			} else {
				if (get(i).equalsIgnoreCase(term))
					results.add(i);
			}
		}
	}

	@Override
	public void clear() {
		//

	}

	@Override
	public void write(File termsFile) {
		//

	}

	@Override
	public String get(Integer id) {
		return words[id];
	}

	@Override
	public int numberOfTerms() {
		return words.length;
	}

	@Override
	public int idToSortPosition(int id, boolean sensitive) {
		//
		return id;
	}

	@Override
	protected void setBlockBasedFile(boolean useBlockBasedTermsFile) {
		//

	}

	@Override
	public boolean termsEqual(int[] termId, boolean caseSensitive, boolean diacSensitive) {
		throw new UnsupportedOperationException();
	}

}
