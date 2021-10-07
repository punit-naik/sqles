(ns org.clojars.punit-naik.sqles.query
  (:require [org.clojars.punit-naik.sqles.config :as config]
            [clojure.string :as str]))

(defn remove-quotes
  "Removes single/double quotes from a string"
  [s]
  (str/replace s #"\"|\'" ""))

(defn select
  [fields]
  (let [fields (if (coll? fields) fields [fields])]
    (when-not (seq
               (some (fn [field]
                       (re-matches #".*\*" field))
                    fields))
      {:_source (map keyword fields)})))

(defn from
  [table]
  (str (config/es-server) table))

(defn op->op-key
  [op]
  (get {"=" :equals
        "<" :less-than
        "<=" :less-than-or-equals
        ">" :greater-than
        ">=" :greater-than-or-equals
        "!=" :not-equals
        "<>" :not-equals
        "in" :in-set
        "between" :between-range-incl}
       op))

(defn term-query
  [field value]
  (let [field (str field ".keyword")
        value (remove-quotes value)]
    {:term {(keyword field) value}}))

(defmulti where
  (fn [_ op _] (op->op-key op)))

(defmethod where :equals
  [field _ value]
  (try (let [read-value (read-string value)]
         (if (number? read-value)
           (where field "between"
                  (str "(" read-value " " read-value")"))
           (term-query field value)))
       (catch Exception _
         (term-query field value))))

(defmethod where :less-than
  [field _ value]
  {:range {(keyword field) {:lt (read-string value)}}})

(defmethod where :less-than-or-equals
  [field _ value]
  {:range {(keyword field) {:lte (read-string value)}}})

(defmethod where :greater-than
  [field _ value]
  {:range {(keyword field) {:gt (read-string value)}}})

(defmethod where :greater-than-or-equals
  [field _ value]
  {:range {(keyword field) {:gte (read-string value)}}})

(defmethod where :between-range-incl
  [field _ val-range]
  (let [[gte lte] (read-string val-range)]
    {:range {(keyword field) {:lte lte :gte gte}}}))

(defmethod where :not-equals
  [field _ value]
  (where field "=" value))

(defmethod where :in-set
  [field _ values]
  (let [field (str field ".keyword")
        values (read-string values)]
    {:terms {(keyword field) values}}))

(defn where->es
  "Converts SQL Where clauses into Elasticsearch boolean logic
   NOTE: Parsing of nested logical statements using brackets is not supported yet"
  [statements]
  (update-in
   {:query
    {:bool
     (cond-> {:must
              (map (fn [ands]
                     (if (map? ands)
                       (:query (where->es ands))
                       (apply where ands)))
                   (-> statements :and :true))
              :should (concat
                       (map (fn [ors]
                              (if (map? ors)
                                (:query (where->es ors))
                                (apply where ors)))
                            (-> statements :or :true))
                       (map (fn [s]
                              {:bool
                               {:must_not (if (map? s)
                                            (:query (where->es s))
                                            (apply where s))}})
                            (-> statements :or :false)))}

       (seq (-> statements :and :false))
       (merge {:must_not
               (map (fn [false-ands]
                      (if (map? false-ands)
                        (:query (where->es false-ands))
                        (apply where false-ands)))
                    (-> statements :and :false))}))}}
   [:query :bool]
   (fn [{:keys [should must] :as m}]
     (cond-> m
       (empty? should) (dissoc :should)
       (empty? must) (dissoc :must)))))
