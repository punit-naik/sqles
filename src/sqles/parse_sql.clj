(ns sqles.parse-sql
  (:require [clojure.string :as str]
            [sqles.query :as query]
            [sqles.parse-sql.where :as where]))

(defn query->es-op
  [clause]
  (let [clause (str/lower-case clause)]
    (get {"select" "/_search"}
         clause)))

(defn clause->query-fn
  [clause]
  (let [clause (str/lower-case clause)]
    (get {"select" query/select
          "where" query/where
          "from" (fn [_])}
         clause)))

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

(defn clean-clause-data
  "Cleans clause data by removing commas from fields
   For the `select` clause"
  [clause-data]
  (mapcat (fn [cd] (filter seq (str/split cd #","))) clause-data))

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
              select-clause? (= (str/lower-case clause) "select")
              where-clause? (= (str/lower-case clause) "where")
              clause-data (cond-> (take-till-next-clause (rest ps))
                            select-clause? clean-clause-data
                            where-clause? where/handle-where-clause-data)
              intermediate-es-query ((clause->query-fn clause) clause-data)
              remaining-parts (drop (count clause-data) (rest ps))]
          (recur remaining-parts
                 (update result :body merge intermediate-es-query)))))))