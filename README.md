# apraxis

A Clojure FRAMEWORK designed to create fancy dan applications. Fancy dan will be elaborated later.

## Usage

To create a new Apraxis project with the name `my-project`, run:

```bash
lein new apraxis my-project
```

## Templating

Apraxis has templates for

- Client-side components
- Single-page apps

These templates are accessible either through the REPL in the `apraxis.templates` namespace, or as individual leiningen tasks, e.g.:

```clj
(apraxis.templates.component/generate my-component)
```

or

```bash
lein generate component my-component
```

are semantically equivalent.

### Component Generator

Given a component name, the component generator creates four files under four directories. For the component named `my-component`, the files are:

- `src/client/components/my_component.cljs`
- `src/structure/components/_my_component.haml`
- `src/style/components/_my_component.scss`
- `src/sample/my_component.edn`

### Single-page App Generator

The single-page app generator creates a single full HTML document, and corresponding cljs main file. For the single-page app named `my-app`, the files are:

- `src/client/pages/my_app_main.cljs`
- `src/structure/pages/my_app.haml`

`my_app.haml` implicitly loads `my_app_main.cljs` and execute the `-main` function in that namespace, if `my_app_main.cljs` exists. If `my_app_main.cljs` does not exist, the contents of `my_app.haml` are presented verbatim as a static web page.

## Incremental Building Pipeline

Apraxis includes an asset pipeline that consumes HAML, SASS, ClojureScript, EDN, and CLJX, and emits JavaScript, CSS, and HTML describing the behavior of a single-page client side app. The flow of this pipeline can be visualized as:

    /----------\  /----------\
    |HAML      |->|HTML      |\
    \----------/  \----------/ \
                                \
    /----------\                 \  /----------\
    |CLJS      |------------------->|JavaScript|
    \----------/                 /  \----------/
                                /
    /----------\  /----------\ /
    |CLJX      |->|CLJS      |/
    \----------/  \----------/

    /----------\                    /----------\
    |SASS      |------------------->|CSS       |
    \----------/                    \----------/

    /----------\  /----------\      /----------\
    |EDN       |->|Apraxis   |----->|JavaScript|
    \----------/  \----------/\     \----------/
                               \
                                \   /----------\
                                 \->|HTML      |
                                    \----------/


HTML, CLJS, and CLJX specify structure and behavior for Om components. SASS controls the visual styling of the component when included in a page, and the Apraxis framework, along with EDN sample data files, generate scaffolding and a layout page for the component to live in. EDN data are synthesized to an Om cursor and provided to the component for testing.

## Client Service Integration

Apraxis creates a Pedestal service with tools included for client side integration and API development:

- Datomic DB auto-fetched and associated with requests for data consistency.
- Scaffolding service that builds a containing environment for components to iteratively develop them in isolation.
- Application service that monitors development facilities such as [Figwheel](https://github.com/bhauman/lein-figwheel) and the [CLJS REPL](https://github.com/tomjakubowski/weasel), and configures clients to use them.
- Host matching to initialize client side applications to communicate with an Apraxis service that serves them.

## Testing

When generating a component, tests are generated that run in <fill in blank browser here later>. The tests examine the behavior of the component in isolation using sample data.

When generating a single-page app, tests are generated that exercise the app in an end-to-end fashion--that is, with a running Apraxis service backing the single-page app.

## Shipping

`lein jar` runs the entire asset pipeline from all sources, creates an executable Apraxis service, and packages it as an executable jar. You must ensure that all its dependencies are on the classpath when running this jar.

`lein with-profile staging jar` generates the same jar as above, but with the staging profile. By default, it does not run advanced mode compilation when generating the jar to make the generated code more amenable to debugging.

`lein uberjar` generates a similar jar with an executable Apraxis service, but also packages all dependencies into the produced jar.

## License

Copyright © 2015-2016 Alex Redington, Joe R. Smith, Michael Parenteau, and Sam Umbach

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
