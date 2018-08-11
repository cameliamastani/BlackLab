/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyLeftContext extends HitProperty {

    private String luceneFieldName;

    private Annotation annotation;

    private Terms terms;

    private boolean sensitive;

    public HitPropertyLeftContext(Hits hits, Annotation annotation, boolean sensitive) {
        super(hits);
        BlackLabIndex index = hits.queryInfo().index();
        this.annotation = annotation == null ? hits.queryInfo().field().annotations().main() : annotation;
        this.luceneFieldName = this.annotation.luceneFieldPrefix();
        this.terms = index.forwardIndex(this.annotation).terms();
        this.sensitive = sensitive;
    }

    public HitPropertyLeftContext(Hits hits, Annotation annotation) {
        this(hits, annotation, hits.queryInfo().index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyLeftContext(Hits hits, boolean sensitive) {
        this(hits, hits.queryInfo().field().annotations().main(), sensitive);
    }

    public HitPropertyLeftContext(Hits hits) {
        this(hits, null, hits.queryInfo().index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyLeftContext(BlackLabIndex index, Annotation annotation, boolean sensitive) {
        super(null);
        this.annotation = annotation == null ? index.mainAnnotatedField().annotations().main(): annotation;
        this.terms = index.forwardIndex(this.annotation).terms();
        this.sensitive = sensitive;
    }

    public HitPropertyLeftContext(BlackLabIndex index, boolean sensitive) {
        this(index, null, sensitive);
    }

    @Override
    public HitProperty copyWithHits(Hits newHits) {
        return new HitPropertyLeftContext(newHits, annotation, sensitive);
    }

    @Override
    public HitPropValueContextWords get(int hitNumber) {
        int[] context = contexts.getHitContext(hitNumber);
        int contextHitStart = context[Contexts.CONTEXTS_HIT_START_INDEX];
        //int contextRightStart = context[Contexts.CONTEXTS_RIGHT_START_INDEX];
        int contextLength = context[Contexts.CONTEXTS_LENGTH_INDEX];

        // Copy the desired part of the context
        int n = contextHitStart;
        if (n <= 0)
            return new HitPropValueContextWords(hits, annotation, new int[0], sensitive);
        int[] dest = new int[n];
        int contextStart = contextLength * contextIndices.get(0) + Contexts.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
        System.arraycopy(context, contextStart, dest, 0, n);

        // Reverse the order of the array, because we want to sort from right to left
        for (int i = 0; i < n / 2; i++) {
            int o = n - 1 - i;
            // Swap values
            int t = dest[i];
            dest[i] = dest[o];
            dest[o] = t;
        }
        return new HitPropValueContextWords(hits, annotation, dest, sensitive);
    }

    @Override
    public int compare(Object i, Object j) {
        //Hit a = hits.getByOriginalOrder((Integer)i);
        //Hit b = hits.getByOriginalOrder((Integer)j);
        int[] ca = contexts.getHitContext((Integer) i);
        int caHitStart = ca[Contexts.CONTEXTS_HIT_START_INDEX];
        int caLength = ca[Contexts.CONTEXTS_LENGTH_INDEX];
        int[] cb = contexts.getHitContext((Integer) j);
        int cbHitStart = cb[Contexts.CONTEXTS_HIT_START_INDEX];
        int cbLength = cb[Contexts.CONTEXTS_LENGTH_INDEX];

        // Compare the left context for these two hits, starting at the end
        int contextIndex = contextIndices.get(0);
        int ai = caHitStart - 1;
        int bi = cbHitStart - 1;
        while (ai >= 0 && bi >= 0) {
            int cmp = terms.compareSortPosition(
                    ca[contextIndex * caLength + ai + Contexts.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS],
                    cb[contextIndex * cbLength + bi + Contexts.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS], sensitive);
            if (cmp != 0)
                return reverse ? -cmp : cmp;
            ai--;
            bi--;
        }
        // One or both ran out, and so far, they're equal.
        if (ai < 0) {
            if (bi >= 0) {
                // b longer than a => a < b
                return reverse ? 1 : -1;
            }
            return 0; // same length; a == b
        }
        return reverse ? -1 : 1; // a longer than b => a > b
    }

    @Override
    public List<Annotation> needsContext() {
        return Arrays.asList(annotation);
    }

    @Override
    public String getName() {
        return "left context";
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList("left context: " + annotation.name());
    }

    @Override
    public String serialize() {
        String[] parts = AnnotatedFieldNameUtil.getNameComponents(luceneFieldName);
        String thePropName = parts.length > 1 ? parts[1] : "";
        return serializeReverse() + PropValSerializeUtil.combineParts("left", thePropName, sensitive ? "s" : "i");
    }

    public static HitPropertyLeftContext deserialize(Hits hits, String info) {
        String[] parts = PropValSerializeUtil.splitParts(info);
        AnnotatedField field = hits.queryInfo().field();
        String propName = parts[0];
        if (propName.length() == 0)
            propName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        boolean sensitive = parts.length > 1 ? parts[1].equalsIgnoreCase("s") : true;
        Annotation annotation = field.annotations().get(propName);
        return new HitPropertyLeftContext(hits, annotation, sensitive);
    }

}
