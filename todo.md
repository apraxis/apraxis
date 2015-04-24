* Create apraxis component rendering jig. Renders component once per each chunk of data in example edn.
* HTML doc goog.requires jig, and goog.requires the targeted component. The targeted component is filled in by enlive in pedestal.
* EDN updates should provoke live updates to the example components.
* HTML doc invokes jig with target component, and with EDN
* Open questions:
    * Use figwheel for iterative delivery of JS? If so, hack together EDN delivery by js delivery?
    * Any JS delivery needs to ultimately give us the ability to trigger a deep rerender of OM.
    * If Figwheel gives us an arbitrary hook to trigger on reload, then we can use that + om dismount -> om mount to force a full rerender.
* wrap component body in an HTML element (probably a <div>) and select that element in our `defsnippet`
    * also consider the case where the component HAML specifies a component root element (so we don't have to wrap every component an additional <div>)
    * or we could make it a rule that component HAML files **must** have a single root element that wraps the entire component contents
