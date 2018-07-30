(defproject
  eigform "0.0.1"
  :url "https://github.com/entrepreneur-interet-general/eigform"
  :license {:name "Eclipse Public License 2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]
                 ;; [org.clojure/data.xml "0.0.8"]
                 ;; [org.clojure/data.zip "0.1.2"]
                 ;; [cheshire "5.8.0"]
                 [org.clojure/data.csv "0.1.4"]
                 [clj-http "3.9.0"]
                 [semantic-csv "0.2.0"]
                 [compojure "1.6.1"]
                 [hiccup "1.0.5"]
                 [ring "1.6.3"]
                 [http-kit "2.3.0"]
                 [com.draines/postal "2.0.2"]
                 [aero "1.1.3"]]
  :description "eigform: ad hoc form for EIG 3"
  :main eigform.handler
  :profiles {:uberjar {:aot :all}})
