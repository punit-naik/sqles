(ns sqles.parse-sql.where-test
  (:require [clojure.test :refer [deftest is]]
            [sqles.parse-sql.where :as where]))

(deftest un-separated-operands?-test
  (is (= (where/un-separated-operands? "a=1") "="))
  (is (nil? (where/un-separated-operands? "a = 1"))))

(deftest separate-operands-test
  (is (= (where/separate-operands ["a=1" "b=2"]) ["a" "=" "1" "b" "=" "2"]))
  (is (= (where/separate-operands ["c=3" "d = 4"]) ["c" "=" "3" "d = 4"])))

(deftest nested-handle-clause-data-test
  (is (= (where/nested-handle-clause-data ["(b!=2 or c!=3)"])
         {:and {:true [] :false []}
          :or {:true [] :false [["b" "!=" "2"] ["c" "!=" "3"]]}}))
  (is (= (where/nested-handle-clause-data ["(a!=1 or b=3)"])
         {:and {:true [] :false []}
          :or {:true [["b" "=" "3"]] :false [["a" "!=" "1"]]}}))
  (is (= (where/nested-handle-clause-data ["(a!=1 and b=3)"])
         {:or {:true [] :false []}
          :and {:true [["b" "=" "3"]] :false [["a" "!=" "1"]]}}))
  (is (= (where/nested-handle-clause-data ["(b!=2 and c!=3)"])
         {:or {:true [] :false []}
          :and {:true [] :false [["b" "!=" "2"] ["c" "!=" "3"]]}})))

(deftest separate-statements-test
  (is (= (where/separate-statements ["a" "=" "1" "and" "(b!=2 or c!=3)"])
         {:and [["a" "=" "1"]
                {:and {:true [] :false []}
                 :or {:true [] :false [["b" "!=" "2"] ["c" "!=" "3"]]}}]
          :or []}))
  (is (= (where/separate-statements ["a" "=" "1" "and" "(b=2 or c!=3)"])
         {:and [["a" "=" "1"]
                {:and {:true [] :false []}
                 :or {:true [["b" "=" "2"]] :false [["c" "!=" "3"]]}}]
          :or []})))

(deftest separate-nots-test
  (is (= (where/separate-nots
          [["a" "=" "1"]
           ["b" "!=" "2"]])
         {:true [["a" "=" "1"]] :false [["b" "!=" "2"]]}))
  (is (= (where/separate-nots
          [["a" "=" "1"]
           ["b" "!=" "2"]
           ["c" "=" "3"]
           ["d" "!=" "4"]])
         {:true [["a" "=" "1"] ["c" "=" "3"]]
          :false [["b" "!=" "2"] ["d" "!=" "4"]]}))
  (is (= (where/separate-nots
          [["a" "=" "1"]
           ["b" "not" "in" "(1, 2, 3)"]])
         {:true [["a" "=" "1"]] :false [["b" "in" "(1, 2, 3)"]]})))

(deftest handle-clause-data-test
  (is (= (where/handle-clause-data ["a=1" "and" "(b!=2 or c!=3)"])
         {:and
          {:true [["a" "=" "1"]
                  {:and {:true [] :false []}
                   :or {:true [] :false [["b" "!=" "2"] ["c" "!=" "3"]]}}]
           :false ()}
          :or {:true (), :false ()}}))
  (is (= (where/handle-clause-data ["a=1" "and" "b=2" "or" "c=3" "and" "d!=4" "or" "e" "=" "5"])
         {:and {:true [["a" "=" "1"] ["b" "=" "2"]] :false [["d" "!=" "4"]]}
          :or {:true [["c" "=" "3"] ["e" "=" "5"]] :false []}})))