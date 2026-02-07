(ns org.clojars.punit-naik.sqles.parse-sql-test
  (:require
   [clojure.test :refer [deftest is]]
   [org.clojars.punit-naik.sqles.config :as config]
   [org.clojars.punit-naik.sqles.parse-sql :as parse-sql]
   [org.clojars.punit-naik.sqles.query :as query]
   [org.clojars.punit-naik.sqles.parse-sql.utils :as utils]))

(deftest query->es-op-test
  (is (= "/_search" (parse-sql/query->es-op "select")))
  (is (nil? (parse-sql/query->es-op "where"))))

(deftest clause->query-fn-test
  (is (= query/select (parse-sql/clause->query-fn "select")))
  (is (= query/limit (parse-sql/clause->query-fn "limit")))
  (is (= query/where->es (parse-sql/clause->query-fn "where")))
  (is (nil? ((parse-sql/clause->query-fn "from"))))
  (is (nil? ((parse-sql/clause->query-fn "order"))))
  (is (= query/order-by (parse-sql/clause->query-fn "order by")))
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
  (is (= ["a" "b" "c"] (utils/handle-clause-data "select" ["a , b , c"])))
  (is (= {:and {:true [["a" "=" "\"A, a\""]] :false []}
          :or {:true [] :false []}}
         (utils/handle-clause-data "where"  ["a=\"A, a\""])))
  (is (= {:and {:true [["x" "=" "1"]] :false []}
          :or {:true [] :false []}}
         (utils/handle-clause-data "where" ["x" "=" "1"])))
  (is (= "10" (utils/handle-clause-data "limit" ["10"])))
  (is (= [["id" "desc"] ["id2" "asc"]] (utils/handle-clause-data "order by" ["id" "desc,id2" "asc"])))
  (is (= [["id"]] (utils/handle-clause-data "order by" ["id"])))
  (is (= [["id"] ["id2"]] (utils/handle-clause-data "order by" ["id,id2"]))))

(deftest clean-query-test
  (is (= "select a,b,c from test-1" (parse-sql/clean-query "select a, b, c from test-1")))
  (is (= "select a,b,c from test-1" (parse-sql/clean-query "select a, b , c from test-1")))
  (is (= "select a,b,c from test-1" (parse-sql/clean-query "select a , b , c from test-1")))
  (is (= "select a,b,c from test-1 where a=\"A, a\"" (parse-sql/clean-query "select a, b, c from test-1 where a=\"A, a\"")))
  (is (= "select a,b,c from test-1 where a='A, a'" (parse-sql/clean-query "select a, b, c from test-1 where a='A, a'")))
  (is (= "select a from test where b=nil"
         (parse-sql/clean-query "select a from test where b=NULL"))))

(deftest order-by-invalid-test
  (is (thrown? AssertionError
               (utils/handle-clause-data "order by" ["id" "descending"]))))

(deftest count-query?-test
  (is (parse-sql/count-query? "select count(*) from test"))
  (is (not (parse-sql/count-query? "select a, count(*) from test"))))

(deftest parse-query-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly true)]
    (is (= {:url "http://localhost:9200/test-1/_search"
            :body {:_source [:a :b]}
            :method :post}
           (parse-sql/parse-query "select a , b from test-1")))
    (is (= {:url "http://localhost:9200/test-1/_search"
            :body {:_source [:a :b :c]}
            :method :post}
           (parse-sql/parse-query "select a , b,c from test-1")))
    (is (= {:url "http://localhost:9200/test-1/_search"
            :body {:query {:match_all {}}}
            :method :post}
           (parse-sql/parse-query "select * from test-1")))
    (is (= {:url "http://localhost:9200/test-4/_search"
            :body {:query {:bool {:must [{:range {:id {:lte 10 :gte 1}}}]
                                  :must_not [{:term {:name.keyword "Bob-2"}}]}}}
            :method :post}
           (parse-sql/parse-query "select * from test-4 where id between (1,10) and name!=Bob-2")))
    (is (= {:url "http://localhost:9200/test-5/_search"
            :body {:query {:match_all {}}
                   :size "2"}
            :method :post}
           (parse-sql/parse-query "select * from test-5 limit 2")))
    (is (= {:url "http://localhost:9200/test/_search"
            :body {:query {:match_all {}}
                   :sort [{:id {:order "asc"}}]
                   :size "10"}
            :method :post}
           (parse-sql/parse-query "select * from test order by id limit 10")))
    (is (= {:url "http://localhost:9200/test/_search"
            :body {:query {:match_all {}}
                   :sort [{:id {:order "desc"}}
                          {:id2 {:order "asc"}}]
                   :size "10"}
            :method :post}
           (parse-sql/parse-query "select * from test order by id desc, id2 asc limit 10")))
    (is (= {:url "http://localhost:9200/test/_search"
            :body {:query {:match_all {}}
                   :sort [{:id {:order "asc"}}
                          {:id2 {:order "asc"}}]
                   :size "10"}
            :method :post}
           (parse-sql/parse-query "select * from test order by id, id2 limit 10")))
    (is (= {:url "http://localhost:9200/job/_count"
            :body {:query {:bool {:must [{:range {:job_id {:gt 1000}}} {:range {:job_id {:lt 400000000}}}]}}}
           :method :post}
           (parse-sql/parse-query "select count(*) from job where job_id > 1000 and job_id < 400000000")))))

(deftest parse-query-server-down-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly false)]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Elasticsearch server is unavailable"
         (parse-sql/parse-query "select * from test")))))

(deftest query->es-op-case-test
  (is (= "/_search" (parse-sql/query->es-op "SeLeCt")))
  (is (= "/_count" (parse-sql/query->es-op "COUNT")))
  (is (nil? (parse-sql/query->es-op "FROM"))))

(deftest get-clause-test
  (is (= ["select"] (parse-sql/get-clause ["select" "*"])))
  (is (= ["order" "by"] (parse-sql/get-clause ["order" "by"])))
  (is (= ["group" "by"] (parse-sql/get-clause ["group" "by"]))))

(deftest take-till-next-clause-quoted-test
  (is (= ["name=\"a b\""]
         (parse-sql/take-till-next-clause ["name=\"a b\"" "from" "t"]))))

(deftest clean-query-brackets-test
  (is (= "select * from t where id between (1,2)"
         (parse-sql/clean-query "select * from t where id between (1, 2)")))
  (is (= "select * from t where name=\"(a,b)\""
         (parse-sql/clean-query "select * from t where name=\"(a, b)\""))))

(deftest handle-select-clause-data-empty-test
  (is (= () (utils/handle-clause-data "select" [""])))
  (is (= [""] (utils/handle-clause-data "select" [" "]))))

(deftest parse-query-order-only-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly true)]
    (is (= {:url "http://localhost:9200/test/_search"
            :body {:query {:match_all {}}
                   :sort [{:id {:order "asc"}}]}
            :method :post}
           (parse-sql/parse-query "select * from test order by id")))))

(deftest parse-query-where-in-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly true)]
    (is (= {:url "http://localhost:9200/test/_search"
            :body {:query {:bool {:must [{:terms {:id.keyword [1 2 3]}}]}}}
            :method :post}
           (parse-sql/parse-query "select * from test where id in (1, 2, 3)")))))
