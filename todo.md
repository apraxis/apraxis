* HTML doc invokes jig with target component, and with EDN
* Open questions:
    * Use figwheel for iterative delivery of JS? If so, hack together EDN delivery by js delivery?
    * Any JS delivery needs to ultimately give us the ability to trigger a deep rerender of OM.
    * If Figwheel gives us an arbitrary hook to trigger on reload, then we can use that + om dismount -> om mount to force a full rerender.
* wrap component body in an HTML element (probably a <div>) and select that element in our `defsnippet`
    * also consider the case where the component HAML specifies a component root element (so we don't have to wrap every component an additional <div>)
    * or we could make it a rule that component HAML files **must** have a single root element that wraps the entire component contents

* advanced mode?
    * different handling for react.js and other 3rd-party dependencies
* sort out resource path
    * apraxis needs to make some resources available (e.g., component jig HTML)
    * also need to find and serve up output from Middleman, cljsbuild, etc
 * sort out URLs
     * where do we serve out js from cljsbuild? third-party js? CSS? HTML?
* styling (and possibly more markup) for jig and component wrapper
    * make the boundaries of the component clear
    * how do we handle components that are meant to be a specific size? or meant to be 100% width? or are positioned absolute?
 * apraxis dev workflow
    * how do we make it easy to iterate and avoid `lein install` and restarting the repl and web server all the time?
* app dev workflow
    * auto build of cljs (when template/snippet input changes)
    * easy to start server and autobuild
    * auto reloading in browser based on server change
        * figwheel may help here
        * even active reload might be a reasonable solution
 
