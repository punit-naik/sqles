(ns sqles.parse-sql
  (:require [clojure.string :as str]
            [sqles.query :as query]
            [sqles.parse-sql.utils :refer [handle-clause-data]]))

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
            "where" query/where
            "from" (constantly nil)}
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
  (->> (mapcat (fn [cd] (filter seq (str/split cd #","))) clause-data)
       (map str/trim)))

(defn clean-query
  "Removes spaces before and after commas
   Won't match commas between quotes, single or double"
  [query]
  (str/replace query #"(?!\B\"[^(\"|\')]*)[\s+]?,(?![^(\"|\')]*(\"|\')\B)\s+" ","))

(defn parse-query
  [sql-query]
  (let [sql-query (clean-query sql-query)
        index (find-index sql-query)
        parts (str/split sql-query #" ")]
    (loop [ps parts
           result {:url (str (query/from index)
                             (query->es-op (first parts)))
                   :body {}}]
      (if (empty? ps)
        result
        (let [clause (str/lower-case (first ps))
              clause-data (take-till-next-clause (rest ps))
              remaining-parts (drop (count clause-data) (rest ps))
              clause-data (handle-clause-data clause clause-data)
              intermediate-es-query ((clause->query-fn clause) clause-data)]
          (recur remaining-parts
                 (update result :body merge intermediate-es-query)))))))
