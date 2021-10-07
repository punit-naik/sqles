(defproject sqles "0.1.0"
  :description "A Clojure library designed to convert SQL statements into Elasticsearch queries"
  :url "https://github.com/punit-naik/sqles.git"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.grammarly/omniconf "0.4.3"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.1"]]
  :profiles {:es-dev {:jvm-opts ["-Des-hostname=localhost" "-Des-port=9200"]}}
  :repl-options {:init-ns sqles.core}
  :main sqles.core)
