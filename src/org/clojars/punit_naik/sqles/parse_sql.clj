(ns org.clojars.punit-naik.sqles.parse-sql
  (:require [clojure.string :as str]
            [org.clojars.punit-naik.sqles.query :as query]
            [org.clojars.punit-naik.sqles.parse-sql.utils :refer [handle-clause-data]]
            [org.clojars.punit-naik.sqles.parse-sql.where :as where]))

(defn query->es-op
  [clause]
  (let [clause (str/lower-case clause)]
    (get {"select" "/_search"}
         clause)))

(defn clause->query-fn
  [clause]
  (when clause
    (let [clause (str/lower-case clause)]
      (get {"select" query/select
            "where" query/where->es
            "from" (constantly nil)
            "limit" query/limit
            "order by" query/order-by
            "order" (constantly nil)
            "group by" query/group-by
            "group" (constantly nil)}
           clause))))

(defn find-index
  "Finds the index to be queried from the SQL query"
  [sql-query]
  (let [parts (->> (str/split sql-query #" ")
                   (map-indexed vector)
                   (into {}))
        from-clauses #{"from" "FROM"}]
    (->> (filter (fn [[k _]]
                   (contains? from-clauses (get parts (dec k)))) parts)
         vals first)))

(defn take-till-next-clause
  "Gets the data for a particular clause from query parts
   Till the next clause is found"
  [query-parts]
  (take-while #(not (clause->query-fn %)) query-parts))

(defmethod handle-clause-data "select"
  [_ clause-data]
  (map str/trim
       (mapcat (fn [cd]
                 (remove empty? (str/split cd #",")))
               clause-data)))

(defmethod handle-clause-data "where"
  [_ clause-data]
  (where/handle-clause-data clause-data))

(defmethod handle-clause-data "limit"
  [_ [limit]]
  limit)

(defmethod handle-clause-data "order by"
  [_ clause-data]
  (let [;; NOTE: Joining by `\s` first as we are not splitting the string on `,` in case of multiple `order by` fields
        clause-data-str (str/join " " clause-data)
        clause-data (->> (str/split clause-data-str #",")
                         (map (fn [s] (str/split s #"\s"))))]
    (if-not (every? (fn [[_ order :as x]]
                      (and (<= (count x) 2)
                           (or (nil? order)
                               (= order "asc")
                               (= order "desc")))) clause-data)
      (throw (AssertionError. "Wrong `ORDER BY` clause data"))
      clause-data)))

(defmethod handle-clause-data "group by"
  [_ clause-data]
  (handle-clause-data "select" clause-data))

(defn clean-query
  "Removes spaces before and after commas
   And spaces after opened round bracket and before closed round bracket
   Won't match commas/round brackets between quotes, single or double"
  [query]
  (-> (str/replace query #"(?!\B\"[^(\"|\'|\`)]*)[\s+]?,(?![^(\"|\'|\`)]*(\"|\'|\`)\B)\s+" ",")
      (str/replace #"\((?![^(\"|\'|\`)]*(\"|\'|\`)\B)\s+" "(")
      (str/replace #"(?!\B\"[^(\"|\'|\`)]*)[\s+]?\)" ")")
      (str/replace #"NULL|null" "nil")))

(defn get-clause
  [[first-part second-part]]
  (let [first-part (str/lower-case first-part)]
    (if (contains? #{"group" "order"} first-part)
      [first-part second-part]
      [first-part])))

(defn query-order
  "Checks if the order of the clauses of the SQL query is correct"
  [sql-query]
  )

(defn parse-query
  [sql-query]
  (let [sql-query (clean-query sql-query)
        index (find-index sql-query)
        parts (str/split sql-query
                         #"\s+(?=([^\"\'\`]*[\"|\'|\`][^\"\'\`]*[\"|\'|\`])*[^\"\'\`]*$)")]
    (loop [ps parts
           {{:keys [agg-fields]} :body :as result}
           {:url (str (query/from index)
                      (query->es-op (first parts)))
            :body {}
            :method :post}]
      (if (empty? ps)
        (dissoc result :agg-fields)
        (let [clause-coll (get-clause ps)
              clause (str/lower-case (str/join " " clause-coll))
              remaining-parts (drop (count clause-coll) ps)
              clause-data (take-till-next-clause remaining-parts)
              remaining-parts (drop (count clause-data) remaining-parts)
              clause-data (handle-clause-data clause clause-data)
              intermediate-es-query ((clause->query-fn clause)
                                     (if (= clause "group by")
                                       (if-not (>= (count agg-fields) 1)
                                         (throw (AssertionError. "Wrong `GROUP BY` clause data"))
                                         {:clause-data clause-data :agg-fields agg-fields})
                                       clause-data))]
          (recur remaining-parts
                 (update result :body merge intermediate-es-query)))))))
