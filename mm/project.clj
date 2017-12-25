(defproject mm "0.1.0-SNAPSHOT"
  :description "Military Math"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [keechma "0.3.1"]
                 [reagent "0.7.0"]
                 [funcool/promesa "1.5.0"]
                 [cljsjs/jquery "1.12.4-0"]]
  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-less "1.7.5"]
            [lein-figwheel "0.5.14"]]

  :resource-paths ["public"]

  :less {:source-paths ["less"]
         :target-path "public/css"}


  :figwheel {:http-server-root "."
             :server-ip "0.0.0.0"
             :nrepl-port 7002
             :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]
             :css-dirs ["public/css"]}
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]
                :compiler {:main "mm.core"
                           :output-to "public/js/app.js"
                           :output-dir "public/js/out"
                           :asset-path "js/out"
                           :source-map true
                           :optimizations :none
                           :pretty-print true}
                :figwheel {:on-jsload "mm.core/on-js-reload"}
                }]}
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.13"]
                                  [com.cemerick/piggieback "0.2.2"]]}}

  )
