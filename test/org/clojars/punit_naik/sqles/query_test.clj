(ns org.clojars.punit-naik.sqles.query-test
  (:require
   [clojure.test :refer [deftest is]]
   [org.clojars.punit-naik.sqles.config :as config]
   [org.clojars.punit-naik.sqles.query :as query]
   [org.clojars.punit-naik.sqles.parse-sql.where :as where]))

(deftest remove-quotes-test
  (is (= "Bob" (query/remove-quotes "\"Bob\"")))
  (is (= "Alice" (query/remove-quotes "'Alice'"))))

(deftest select-test
  (is (= {:_source [:a]} (query/select "a")))
  (is (= {:_source [:a :b]} (query/select ["a" "b"])))
  (is (= {:query {:match_all {}}} (query/select ["*"])))
  (is (= {:query {:match_all {}}} (query/select ["a" "*"]))))

(deftest limit-test
  (is (= {:size "10"} (query/limit "10"))))

(deftest from-test
  (with-redefs [config/generate-server-url-from-config (constantly "http://localhost:9200/")
                config/server-up? (constantly true)]
    (is (= "http://localhost:9200/test-1" (query/from "test-1")))
    (is (= "http://localhost:9200/test-2" (query/from "test-2"))))
  (with-redefs [config/es-server (constantly nil)]
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Elasticsearch server is unavailable"
         (query/from "test-3")))))

(deftest op->op-key-test
  (is (->> (map #(let [op-key (query/op->op-key %)]
                   (condp = %
                     "=" (= op-key :equals)
                     "<" (= op-key :less-than)
                     "<=" (= op-key :less-than-or-equals)
                     ">=" (= op-key :greater-than-or-equals)
                     ">" (= op-key :greater-than)
                     "!=" (= op-key :not-equals)
                     "<>" (= op-key :not-equals)
                     false))
                where/operators-used-without-spacing)
           (every? true?))))

(deftest where-test
  (is (= {:range {:a {:lte 1, :gte 1}}} (query/where "a" "=" "1")))
  (is (= {:term {:a.keyword "test"}} (query/where "a" "=" "test")))
  (is (= {:range {:a {:lt 1}}} (query/where "a" "<" "1")))
  (is (= {:range {:a {:gt 1}}} (query/where "a" ">" "1")))
  (is (= {:range {:a {:lte 1}}} (query/where "a" "<=" "1")))
  (is (= {:range {:a {:gte 1}}} (query/where "a" ">=" "1")))
  (is (= {:term {:a.keyword "test"}} (query/where "a" "!=" "test")))
  (is (= {:term {:a.keyword "test"}} (query/where "a" "<>" "test")))
  (is (= {:terms {:a.keyword [1 2]}} (query/where "a" "in" "(1, 2)")))
  (is (= {:terms {:a.keyword ["x" "y"]}} (query/where "a" "in" "(\"x\" \"y\")")))
  (is (= {:range {:a {:gte 1 :lte 3}}} (query/where "a" "between" "(1, 3)")))
  (is (= {:range {:price {:gte 10 :lte 20}}} (query/where "price" "between" "(10 20)"))))

(deftest where->es-test
  (is (= (query/where->es
          {:and {:true [["a" "=" "1"]
                        ["b" "=" "2"]]
                 :false [["d" "!=" "4"]]}
           :or {:true [["c" "=" "3"]] :false [["e" "!=" "5"]]}})
         {:query
          {:bool
           {:must [{:range {:a {:lte 1, :gte 1}}}
                   {:range {:b {:lte 2, :gte 2}}}]
            :should [{:range {:c {:lte 3, :gte 3}}}
                     {:bool {:must_not {:range {:e {:lte 5, :gte 5}}}}}]
            :must_not [{:range {:d {:lte 4, :gte 4}}}]}}}))
  (is (= (query/where->es
          {:and
           {:true [["a" "=" "1"]
                   {:and {:true [], :false []}
                    :or {:true [], :false [["b" "!=" "2"] ["c" "!=" "3"]]}}]
            :false []}
           :or {:true [], :false []}})
         {:query
          {:bool
           {:must [{:range {:a {:lte 1, :gte 1}}}
                   {:bool
                    {:should [{:bool {:must_not {:range {:b {:lte 2, :gte 2}}}}}
                              {:bool {:must_not {:range {:c {:lte 3, :gte 3}}}}}]}}]}}})))

(deftest where->es-empty-branches-test
  (is (= {:query {:bool {:must [{:range {:a {:lte 1 :gte 1}}}]}}}
         (query/where->es {:and {:true [["a" "=" "1"]] :false []}
                           :or {:true [] :false []}}))))

(deftest order-by-test
  (is (= (query/order-by [["id"]])
         {:sort [{:id {:order "asc"}}]}))
  (is (= (query/order-by [["id" "desc"] ["price" "desc"]])
         {:sort [{:id {:order "desc"}}
                 {:price {:order "desc"}}]})))
