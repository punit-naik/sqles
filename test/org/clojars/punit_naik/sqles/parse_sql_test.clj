(ns org.clojars.punit-naik.sqles.parse-sql-test
  (:require [clojure.test :refer [deftest is]]
            [org.clojars.punit-naik.sqles.config :as config]
            [org.clojars.punit-naik.sqles.parse-sql :as parse-sql]
            [org.clojars.punit-naik.sqles.query :as query]
            [org.clojars.punit-naik.sqles.parse-sql.utils :as utils]))

(deftest query->es-op-test
  (is (= "/_search" (parse-sql/query->es-op "select")))
  (is (nil? (parse-sql/query->es-op "where"))))

(deftest clause->query-fn-test
  (is (= query/select (parse-sql/clause->query-fn "select")))
  (is (= query/where->es (parse-sql/clause->query-fn "where")))
  (is (nil? ((parse-sql/clause->query-fn "from"))))
  (is (nil? (parse-sql/clause->query-fn nil))))

(deftest find-index-test
  (is (= "test-1" (parse-sql/find-index "select * from test-1")))
  (is (= "test-2" (parse-sql/find-index "select * from test-2"))))

(deftest take-till-next-clause-test
  (is (= ["a" "b"] (parse-sql/take-till-next-clause ["a" "b" "from" "test-1"])))
  (is (= ["a" "b" "c"] (parse-sql/take-till-next-clause ["a" "b" "c" "from" "test-2"])))
  (is (= ["test-3"] (parse-sql/take-till-next-clause ["test-3" "where" "x" "=" 1]))))

(deftest handle-select-clause-data-test
  (is (= ["a" "b" "c"] (utils/handle-clause-data "select" ["a," "b ," "c"])))
  (is (= ["a" "b" "c"] (utils/handle-clause-data "select" [" a," "b ," "c"])))
  (is (= ["a" "b" "c"] (utils/handle-clause-data "select" ["a, b, c"])))
  (is (= ["a" "b" "c"] (utils/handle-clause-data "select" ["a , b , c"]))))

(deftest clean-query-test
  (is (= "select a,b,c from test-1" (parse-sql/clean-query "select a, b, c from test-1")))
  (is (= "select a,b,c from test-1" (parse-sql/clean-query "select a, b , c from test-1")))
  (is (= "select a,b,c from test-1" (parse-sql/clean-query "select a , b , c from test-1")))
  (is (= "select a,b,c from test-1 where a=\"A, a\"" (parse-sql/clean-query "select a, b, c from test-1 where a=\"A, a\"")))
  (is (= "select a,b,c from test-1 where a='A, a'" (parse-sql/clean-query "select a, b, c from test-1 where a='A, a'"))))

(deftest parse-query-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly true)]
    (is (= {:url "http://localhost:9200/test-1/_search"
            :body {:_source [:a :b]}}
           (parse-sql/parse-query "select a , b from test-1")))
    (is (= {:url "http://localhost:9200/test-1/_search"
            :body {:_source [:a :b :c]}}
           (parse-sql/parse-query "select a , b,c from test-1")))
    (is (= {:url "http://localhost:9200/test-1/_search"
            :body {}}
           (parse-sql/parse-query "select * from test-1")))
    (is (= {:url "http://localhost:9200/test-4/_search"
            :body {:query {:bool {:must [{:range {:id {:lte 10, :gte 1}}}]
                                  :must_not [{:term {:name.keyword "Bob-2"}}]}}}}
           (parse-sql/parse-query "select * from test-4 where id between (1,10) and name!=Bob-2")))
    ;; TODO: Need to add more complex queries
    ))