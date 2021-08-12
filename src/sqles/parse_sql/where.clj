(ns sqles.parse-sql.where
  (:require [clojure.string :as str]))

(def operators-used-without-spacing
  "Operatos which can be used without spacing between operands"
  #{"="
    "<"
    "<="
    ">"
    ">="
    "!="})

(defn un-separated-operands?
  "Checks if the operands of the where statement are separated (by space) or not
   For e.g. Operands are separated in the case of \"a = 1\"
            And not separated in casde of \"a=1\"
   Returns the matched operation in the statement, if un-separated."
  [statement]
  (when-let [matched-op (some #(and (str/includes? statement %) %)
                              operators-used-without-spacing)]
    (when-not (re-matches (re-pattern (str "\\s+\\" matched-op "\\s+")) statement)
      matched-op)))

(defn separate-operands
  "If un-separated operands are present, this function separates them and returns"
  [statements]
  (->> statements
       (map (fn [statement]
              (if (and (not (str/starts-with? statement "("))
                       (not (str/ends-with? statement ")")))
                (let [op (un-separated-operands? statement)]
                  (if (or (= (count statement) 1)
                          (not op))
                    statement
                    (interpose
                     op
                     (str/split statement
                                (re-pattern (str "\\" op)) 2))))
                statement)))
       flatten))

(declare handle-clause-data)

(defn nested-handle-clause-data
  "Applies `handle-clause-data` fn to nested where clauses"
  [clause-data]
  (-> clause-data first
      (str/replace #"\(|\)" "")
      (str/split #"\s")
      handle-clause-data))

(defn separate-statements
  "Seprates where clause statements based on the logical operators they are tied with"
  [clause-data]
  (loop [cd clause-data
         {:keys [un-decided] :as result} {:and [] :or []}]
    (if (empty? cd)
      (let [remove-nil-fn (partial remove nil?)]
        (cond-> (-> result
                    (update :and remove-nil-fn)
                    (update :or remove-nil-fn))
          un-decided (update :and conj un-decided)
          un-decided (dissoc :un-decided)))
      (let [fcd (-> cd first str/lower-case)
            logical? (or (= "and" fcd) (= "or" fcd))
            nested? (and (str/starts-with? fcd "(") (str/ends-with? fcd ")"))]
        (recur (drop (if nested? 1 (if logical? 4 3)) cd)
               (if logical?
                 (-> result
                     (dissoc :un-decided)
                     (update (keyword fcd) conj un-decided
                             (let [next-clause-data (->> cd (drop 1))
                                   next-nested? (and (str/starts-with?
                                                      (first next-clause-data) "(")
                                                     (str/ends-with?
                                                      (first next-clause-data) ")"))
                                   next-clause-data (take (if next-nested? 1 3) next-clause-data)]
                               (cond-> next-clause-data
                                 next-nested? nested-handle-clause-data))))
                 (assoc result :un-decided (if nested?
                                             nested-handle-clause-data
                                             (take 3 cd)))))))))

(defn separate-nots
  "Separates normal (true) `not` (false) statements from a list"
  [statements]
  (let [predicate (fn [s]
                    (if (map? s)
                      false
                      (let [[_ op _] s]
                        (= op "!="))))]
    {:true (remove predicate statements)
     :false (filter predicate statements)}))

(defn handle-clause-data
  [clause-data]
  (->> clause-data
       separate-operands
       separate-statements
       (map (fn [[k statements]]
              [k (separate-nots statements)]))
       (into {})))

(comment
  (handle-clause-data ["a=1" "and" "(b!=2 or c!=3)"])
  (handle-clause-data ["a=1" "and" "b=2" "or" "c=3" "and" "d!=4" "or" "e" "=" "5"]))