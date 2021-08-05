(ns sqles.config-test
  (:require [clojure.test :refer [deftest testing is]]
            [sqles.config :as config]
            [omniconf.core :as cfg]
            [clj-http.client :as client]))

(deftest when-let-multiple-test
  (testing "`when-let-multiple` macro"
    (is (nil? (config/when-let-multiple
               [a 1 b nil]
               (+ a b))))
    (is (= 3 (config/when-let-multiple
              [a 1 b 2]
              (+ a b))))))

(deftest add-trailing-backslash-test
  (is (= "http://google.co.in/"
         (config/add-trailing-backslash "http://google.co.in")))
  (is (= "http://google.co.in/"
         (config/add-trailing-backslash "http://google.co.in/"))))

(deftest ping-server-test
  (is (= (:status (with-redefs [client/get
                                (constantly {:body "{\"active_shards\":1}"
                                             :status 200})]
                    (config/ping-server "http://localhost:9200"))) 200))
  (is (= (:status (with-redefs [client/get
                                (constantly {:body {}})]
                    (config/ping-server "http://google.com"))) 405)))

(deftest server-up?-test
  (is (boolean? (config/server-up? "http://localhost:9200"))))

(deftest generate-server-url-from-config-test
  (is (= ":es-protocol://:es-username::es-password@:es-hostname::es-port"
         (with-redefs [cfg/get identity]
           (config/generate-server-url-from-config)))))

(deftest es-server-test
  (is (= "http://localhost:9200/"
         (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200")
                       config/server-up? (constantly true)]
           (config/generate-server-url-from-config)))))