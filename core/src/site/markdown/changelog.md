# Change Log

## Improvements in HEAD

## Improvements up to v1.7.3

### Fixed
* Fixed waitfortotal parameter not working.
   
## Improvements up to v1.7.2

### New
* Issue warning if unrecognized params passed to Auth* class.

### Changed
* Rewrite queries like [lemma=".*"] to [] for efficiency

### Fixed
* Sort matches for XPaths (VTD-XML does breadth-first search and doesn't 
  necessarily return matches in document order; this can be a problem for 
  more complex XPaths).
* Made timeout longer for hits queries to avoid Timeout error.
* Jackson and commons-compress libraries were updated because of patch 
  security vulnerabilities. 

## Improvements up to v1.7.1

### Changed
* Default maximum file upload size is now 1GB. 

### Fixed
* Don't block until all hits have been counted.
* Fixed bug when maxHitsToRetrieve and maxHitsToCount are the same value

## Improvements up to v1.7.0

### New
* Much user-friendlier indexing using .json/.yaml config files.
  Added support for tabular formats and plain text.
  Includes built-in format configurations for many popular formats.
  See documentation.
* Added "linked document" indexing, which provides e.g. the ability 
  to automatically locate and index the corresponding metadata for 
  each document.
* BLS can now return results in CSV format.
* BLS now supports an "autocomplete" request for metadata fields as well as
  property values. 
* Corpora now have a text direction setting that the user interface can 
  use to change how things are displayed.
* contentViewable can now be specified at the document level as well 
  as corpus-wide, by adding a metadata field named "contentViewable"
  with the value "true" or "false". The per-document setting overrides 
  the corpus-wide setting.
* BLS now allows private corpus owners to explicitly share the corpus with
  other users.
* Many small additions to make it easier to generate a user-friendly
  user interface based on corpus structure information from BlackLab Server,
  such as displayName for fields and values, uiType for field and 
  properties, etc.
* BLS can now omit empty properties in the results if desired. Default is
  to include empty properties. Use requests.omitEmptyProperties setting to 
  change.
* BLS will now send the "Access-Control-Allow-Origin: *" header by 
  default, allowing a frontend on another server to access it.
  This can be overridden in the requests.accessControlAllowOrigin
  setting.
* BLS can attempt to generate a default XSLT from a (XML-based) input format
  configuration.
* Added a MetadataFetcher that reads from CSV files.
* Added plugin capability for document conversion and tagging before indexing,
  allowing BLS to e.g. take ePub input files, convert them to a corpus format, 
  linguistically annotate them and index them. This feature is somewhat 
  experimental and as of yet undocumented.
* Many smaller improvements.

### Changed
* BlackLab now requires Java 8.
* BLS no longer trims leading/trailing whitespace from parameter values. 

### Fixed
* Untokenized fields weren't lowercased while indexing, but were lowercased
  when searching, causing problems. Now they are treated the same.
* Numerous other bugfixes.

## Improvements up to v1.6.0

### New
* Added Searcher.getBlackLabVersion(). Also added blacklabVersion to index.
* Added Hits.filteredBy() to filter Hits on a HitProperty.

### Fixed
* Fixed bug with case-insensitive Terms.indexOf().
* Fixed buffer overrun while writing Terms file.
* Avoid problems with truncating mapped files on Windows. 
* Fixed bug when rewriting "n-grams containing at start/end ..." queries.
* Fixed incorrect matching if ANDNOT has multiple negative clauses.
* Use Arrays.hashCode instead of Object.hashCode for int arrays.
* Fixed "createWeight" bug when filtering on range queries.
* Made it possible to create, close, and re-open an empty index.
* Memory leak in BLS because of bug in TextPattern.rewrite().
* Fixed bug with regexes including character class negations.
* Fixed some rare NullPointerExceptions.

### Changed
* Added an alternative way of matching subqueries, using the forward index
  and a nondeterministic finite automaton (NFA; similar to many regular expression
  engines). Added an optimizer to choose when to use NFA matching.
  Still tweaking, but certain types of queries should be faster with this. 
* Made global constraints on capture groups possible.
* Added HitPropertyHitPosition for fast reproducible sorts.
* Made sure SpansSequenceSimple (which is fast) is used more often.
* Updated library versions, including migration to log4j 2.x.
* Indexer.index(File) now indexes all files by default instead of *.xml.
  If you want to index *.xml, use Indexer.index(File, String glob).
  IndexTool still defaults to *.xml; specify different glob if you want
  to index other file types.
* Upgraded from Lucene 5.2.1 to 5.5.2. Also made some preparations for
  eventually upgrading to Lucene 6.x.
  Deprecated methods that use the deprecated Filter and replaced them with
  a variant that takes Query.
* Removed several (long-)deprecated methods.
* SpanQueryFiltered.rewrite() rewrites its filter as well.
* BLSpanQuery is now the base class of all our SpanQuery classes, and is
  used throughout the code. Only the API hasn't been updated yet, but
  will throw IllegalArgumentException if a SpanQuery argument is not a 
  BLSpanQuery.
* Moved all TextPattern classes to nl.inl.blacklab.search package.
  Moved all SpanQuery/Spans classes to nl.inl.blacklab.search.lucene.
  Made some internal TextPattern methods package-private.
* Renamed SpanQueryBase to BLSpanQueryAbstract.
* Many improvements to documentation based on feedback and new features.
* Many optimizations, large and small.
* Many little fixes.

### BlackLab Server
* Cleaned up logging, made debug logging configurable by subject.
* Improved configuration error handling, made index scanning recursive.
* Added /explain?patt=... to explain how queries are optimized.
* Made long-running count operations pause if client doesn't check status.
* Added parameterized querying (filling in gaps with TSV data). 
* Added hitfiltercrit and hitfilterval parameters to filter hits on a criterium,
  like "word left of matched text". This allows you to view the hits in a single group
  after grouping, and then still allows you to group on these hits again, unlike the
  existing "viewgroup" parameter.
* Fixed cache bug that would cause "Cannot decrement refs, job was already 
  cleaned up!" message
* Made sure control characters are escaped properly in JSON and XML.
* Made sure regular and error output aren't mixed.
* In debug mode, include stacktrace with exception error. 
* Added support for enumerating subproperties.
* Made sure TooManyClauses is caught and dealth with properly.
* Fixed bug when trying to sort hits by a metadata field.
* Improved how context words group property expressions are interpreted.
* Added blacklabVersion to server info page.
* When grouping, show groups from large to small unless another sort is requested.
* Return clean error message when user passes empty group parameter.
* When trying to sample more hits than available, just return all hits.
* If no pattern given, return a proper error message.
* Return sample parameters for all search types, not just ungrouped hits.
* Also include "numberOfHits" stat when viewing group of documents.
* Added error message when 'viewgroup' is used without 'group'.
* Don't add whitespace into leaf XML elements.

## Improvements up to v1.5.0

### Fixed
* Two rare, subtle matching bugs in SpansExpansion and SpansPositionFilter.
* Fixed indexing bug where the compression code would occasionally get stuck in a loop.  

### Changed
* Deprecated TextPattern.toString() methods that take arguments.
* Gave HitProperty and DocProperty default toString() implementation.
* Added methods for iterating over all Lucene documents, forward index documents,
  content store documents.
* Added a test utility that can export your original corpus from the Lucene 
  index and content store (nl.inl.blacklab.testutil.ExportCorpus).
* Many RuntimeExceptions were changed to more specific subclasses like
  IllegalArgumentException or UnsupportedOperationException.
* Made it possible to add a metadata field with a fixed value to every
  document in a single IndexTool run. Useful if you want to combine multiple
  corpora into a single index: add each corpus in a separate IndexTool run,
  with a field Corpus with the appropriate name.

### BlackLab Server
* Made it possible to use POST for queries, so you can execute very large
  queries (many kilobytes). Note that very long (>30KB) regular expressions 
  can trigger problems in Lucene though.
* Allowed previously forbidden all-docs query (now that large document 
  queries are faster and less memory hungry).
* Grouped results are sorted by identity by default now.
* Made docpid a general way to easily filter on a single document PID.
* Replaced building a DataObject tree and serializing it with directly
  streaming the response data using DataStream, saving memory and time.
* Heavily refactored to be more modular.
* Removed some settings related to nonblocking mode, as they didn't seem
  very useful.
* Used JSON.org library instead of copy of the code.
* FIXED: if counting hits takes too long, don't error out but show the 
  results we have.
* FIXED: Do returns results even if counting all the results was interrupted 
  because it took too long.


## Improvements up to v1.4.1

### Fixed
* Potential overflow bug in ContentStoreFixedBlock when retrieving content.
* ContentStoreFixedBlock decompression bug that occurred with highly compressable content.
  (N.B. there is no need to re-index if you experienced either of the above bugs)

### Changed
* Don't store Document objects in DocResults, saving memory and time.
* Changed BL-CQL subproperty separator (e.g. for querying part of speech features separately)
  from ':' to '/'.

### BlackLab Server
* Made sure missing options in blacklab-server.json don't cause problems.

## Improvements up to v1.4.0

### New
* Added experimental support for "subproperties": properties that are indexed in the same Lucene 
  field, using prefixes, but don't each have a forward index. For now, mainly useful for
  indexing each part of speech feature separately, but in the future, BlackLab could possibly move 
  to indexing all properties in a single Lucene field. See DocIndexerOpenSonar,
  QueryExecutionContext.subpropPrefix(). 
* Added HitsSample and its implementation class, which can take a random sample of a larger
  set of hits.
* Added HitPropertyContextWords, which gives the user more options for sorting/grouping on
  context words, such as "group on the first and last words of the matched text" or "group on the
  second and third words to the left of the matched text".

### Changed
* Performed some code cleanups, moved some internal classes to different packages.
* Made Searcher an abstract base class to SearcherImpl; Hits an abstract base class to HitsImpl.
  Added mock classes for Searcher, Hits, ForwardIndex, Terms; used them to add tests.
* Moved hits-related settings from Searcher and Hits into a shared HitsSetting class;
  Searcher has a default set of HitsSettings that Hits objects "inherit".
* Updated gs-collections 6.1 to eclipse-collections 7.1. Replaced Map&lt;Integer, T&gt; with
  IntObjectMap, Map&lt;Integer, Integer&gt; with IntIntMap, ArrayList&lt;Integer&gt; with IntArrayList
  a number of times (mainly) for more memory-efficiency.
* Started using commons-lang to replace certain utility functions.
* Moved some basic Lucene functionality unrelated to the rest of BlackLab from Searcher to 
  LuceneUtil.

## Improvements up to v1.3.7

### Fixed
* Opening a large "fixed-block" content store took a really long time. 

## Improvements up to v1.3.6

### Fixed
* Content store growing larger than 2 GB caused an integer overflow.
* DocIndexersXmlHandlers element matching didn't work correctly for some XML structures.
* Storing document in ContentStoreDirFixedBlock would very rarely crash due to a
  block resizing bug.

## Improvements up to v1.3.5

### New
* Added default unknown condition and value to indextemplate.json, so you can specify what to do if a metadata field value is missing without specifying it for each field separately.

### Fixed
* BLSpanOrQuery would occasionally miss valid hits due to a
  bug in the advanceStartPoint() method.

### Changed
* Switched to JavaCC 6.x.

## Improvements up to v1.3.4

### Fixed
* Lone carriage return characters in JSON output were not escaped;
  Windows line endings were escaped as a single \\n.

## Improvements up to v1.3.3

### Fixed
* Indices with old terms file format (pre-1.3) produced empty concordances.

## Improvements up to v1.3.2

### Fixed
* Query rewrite bug when combining identical clauses with different repetitions,
  i.e. \[pos="AA.*"\]\[pos="AA.*"\]* --> \[pos="AA.*"\]+
* Throw a descriptive error if an index contains no fields.

### Changed
* Some small code quality improvements, like using .isEmpty() instead of .size() == 0.
* Added -javadoc and -sources JARs to Maven build, in preparation for publishing to Maven Central.
* Added distributionManagement section for deploying to OSSRH staging area. 

## Improvements up to v1.3.1

### New
* Added new default content store format "fixedblock", that improves space re-use when updating documents. 

### Fixed
* Bug in SpanQueryAnd which caused incorrect hits to be reported.

## Changed
* Special OSX and Windows files are skipped, even if they occur inside archives.

## Improvements up to v1.3

### Added
* Searcher now implements Closeable, so it can be used with the try-with-resources statement.
* You can specify that properties should not get a forward index using the complexField property "noForwardIndexProps" (space-separated list of property names) in indextemplate.json.

### Fixed
* Forward index terms is no longer limited to around 2 GB.

### Changed

## Improvements up to v1.2.1

### Fixed
* Queries containing only a document filter (metadata filter) would return incorrect results.

## Improvements up to v1.2.0

### Changed
* Switched build from Ant to Maven, and added generating a project site with javadocs, reports, etc.
* Using less memory by switching some Maps in the forward index to the gs-collections one.
* Updated to Lucene 5.2.1.
* Added Maven project site, available at http://inl.github.io/BlackLab/
* Removed Lucene query parser for corpus queries.
* Keep tag end position in payload of start tag, which results in much faster tag searches.
* Rewrote many slower queries to make them (much) faster. Particularly searches with "not" parts and "containing"/"within" should be faster.
* Sped up "containing", "within", and other such filter queries.
* TextPatternAnd was renamed and expanded to TextPatternAndNot. TextPatternAnd is still available as a synonym, but has been deprecated.
* Added TextPatternFilterNGrams to speed up queries of the form: []{2,3} containing "water" (or the "within" equivalent).
* Added BLSpans.advanceStartPosition(target) to "skip" within a document.
* Commons-compress (used for reading .gz files) is statically linked now.
* Limited token length to 1000 (really long tokens would cause problems otherwise).

### Fixed
* Empty version file causes NullPointerException.
* Missing manifest file causes NullPointerException.
* ContentStoreDir contained dependencies on the default encoding.
* A number of subtle search bugs.
* Opening an index by passing a symbolic link throws an exception.
* Miscellaneous small fixes.

## Improvements up to v1.1.0
* Upgraded from Lucene 3.6 to Lucene 4.2. This should speed up regular expression searching, among other things. The required Lucene 4 modules are: core, highlighter, queries, queryparser, analyzers-common. Thanks to Marc Kemps-Sneijders from the Meertens Institute for the code submission!
* The awkwardly-named classes RandomAccessGroup(s) were renamed to HitGroup(s). Also, DocGrouper was renamed to DocGroups to match this naming scheme. The old versions are still around but have been deprecated.
* HitPropValue classes now need a Hits object to properly serialize/deserialize their values in a way that doesn't break after re-indexing.
* Manual object construction was replaced with method calls where possible, for convenience, speed and ease of refactoring. Examples: use Hits.window() instead of new HitsWindow(); use Hits.groupedBy() instead of new ResultsGrouper(); use DocResults.groupedBy() instead of new DocGroups(). (code to the HitGroups/DocGroups APIs instead of to the concrete type ResultsGrouper/DocGrouper). Same for DocResults.
* HitGroups now iterates over HitGroup (used to iterate over Group)
* If you just want to query documents (not find hits for a pattern), use Searcher.queryDocuments(Query) (returns DocResults without hits).
* Preferably use Hits.sortedBy() (returns a new Hits instance) instead of Hits.sort() (modifies Hits instance). In a future version, we want Hits to become immutable to facilitate caching in a multithreaded application. Note that although you get a new Hits instance, the hits themselves are not all copied (no need, because the Hit class is now immutable).
* LuceneQueryParser.allowLeadingWildcard now defaults to false like in Lucene itself. Call LuceneQueryParser.setAllowLeadingWildcard() to change the setting.
* If you want to control how indexing errors are handled, subclass IndexListener and override the errorOccurred() method. This method receives information on what file couldn’t be indexed and why.
* Visibility for some internal classes and methods has been reduced from public to package-private to trim the public API footprint, promoting ease-of-use and facilitating future refactoring. This should not affect applications. If it does affect you, please let me know.
* Some other methods have been renamed, are no longer needed, etc. and have been deprecated. Deprecated methods state the preferred alternative in the @deprecated Javadoc directive.


## Improvements up to v1.0

### Features
* Sorting/grouping on multiple properties now works correctly. Use HitPropertyMultiple.
* You can now sort/group (case-/accent-) sensitively or insensitively.
* You can now easily get a larger snippet of context around a single hit (say, 100 words before and after the hit). Call Hits.getConcordance(String, Hit, int) for this purpose.
* Indexing classes now work using element handlers, making them much more readable. Supporting a new file format has become simpler as a result of this. TEI P4/P5 and FoLiA indexing classes are included with BlackLab now. See nl.inl.blacklab.indexers package.
* It is now possible to delete documents from your index. The forward indices will reuse the free space for new documents.
* The Hits class should be thread-safe now. This makes several things possible: paging through hits without re-executing the query and quickly displaying the first few hits while a background thread fetches the rest. You can even display a running counter while hits are being fetched.
* nl.inl.blacklab.tools.IndexTool is a new generic indexing tool. It is command-line utility that lets you create new indices, add documents to them and delete them again. Indexing can be customized via commandline parameters and/or a properties file. Pass --help for more info.
* QueryTool, the command-line search tool and demonstration program, has been improved with many little features, including a performance test mode.
* Long-running queries may be interrupted using Thread.interrupt(); this will stop the gathering of hits and return control to the caller.
* Hacked in (very) experimental SRU CQL (Contextual Query Language) support. Still needs a bit more love though. :-)

### Performance-/memory-related
* Concordances (for KWIC views) are constructed using the forward indices now (including the one for the new ‘punct’ property, containing punctuation and whitespace between words – if you created your own indexer, it pays to update it to include this property). Before they were constructed using the content store, but this method is much faster and more disk cache friendly. 
* Startup speed has been improved, and there is an option to automatically “warm up” forward indices (i.e. prime the disk cache) in a background thread on startup. Enable this by calling Searcher.setAutoWarmForwardIndices(true); before constructing a Searcher object. This may become the default behaviour in future versions.
* Applications have more control over the maximum number of hits to retrieve, and the maximum hits to count. “Unlimited” is also an option. By default, no more than 1M hits are retrieved, but all hits are counted.
* Several types of queries (notably, phrase searches) have been sped up using the ‘guarantee’ methods in BLSpans to determine when certain operations can be skipped.
* Several other small improvements in performance and memory use.

### Other
* Opening the BlackLab index should now be done using Searcher.open() instead of directly through constructor. See the [https://github.com/INL/BlackLab/commit/d1d1b71ca8d5ef2aea25eab5a6e12b7e51cf5f65 commit message] for the rationale.
* Several superfluous methods were deprecated to simplify the API. The Javadoc will indicate why a method was deprecated and what alternative you should use. Deprecated methods will be removed in the next major version.
* Many small bugs fixed, comments added and code structure improved.
