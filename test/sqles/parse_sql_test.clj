(ns sqles.parse-sql-test
  (:require [clojure.test :refer [deftest is]]
            [sqles.config :as config]
            [sqles.parse-sql :as parse-sql]
            [sqles.query :as query]))

(deftest query->es-op-test
  (is (= "/_search" (parse-sql/query->es-op "select")))
  (is (nil? (parse-sql/query->es-op "where"))))

(deftest clause->query-fn-test
  (is (= query/select (parse-sql/clause->query-fn "select")))
  (is (= query/where (parse-sql/clause->query-fn "where")))
  (is (nil? ((parse-sql/clause->query-fn "from"))))
  (is (nil? (parse-sql/clause->query-fn nil))))

(deftest find-index-test
  (is (= "test-1" (parse-sql/find-index "select * from test-1")))
  (is (= "test-2" (parse-sql/find-index "select * from test-2"))))

(deftest take-till-next-clause-test
  (is (= ["a" "b"] (parse-sql/take-till-next-clause ["a" "b" "from" "test-1"])))
  (is (= ["a" "b" "c"] (parse-sql/take-till-next-clause ["a" "b" "c" "from" "test-2"])))
  (is (= ["test-3"] (parse-sql/take-till-next-clause ["test-3" "where" "x" "=" 1]))))

(deftest hangle-select-clause-data-test
  (is (= ["a" "b" "c"] (parse-sql/hangle-select-clause-data ["a," "b ," "c"])))
  (is (= ["a" "b" "c"] (parse-sql/hangle-select-clause-data [" a," "b ," "c"])))
  (is (= ["a" "b" "c"] (parse-sql/hangle-select-clause-data ["a, b, c"])))
  (is (= ["a" "b" "c"] (parse-sql/hangle-select-clause-data ["a , b , c"]))))

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
           (parse-sql/parse-query "select * from test-1")))))