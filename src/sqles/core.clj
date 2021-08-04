(ns sqles.core
  (:require [sqles.config :as config]))

(defn -main
  [& args]
  (println (config/es-server)))