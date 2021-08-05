(ns sqles.query
  (:require [sqles.config :as config]))

(defn select
  [fields]
  (let [fields (if (coll? fields) fields [fields])]
    (when-not (seq
               (some (fn [field]
                       (re-matches #".*\*" field))
                    fields))
      {:_source fields})))

(defn from
  [table]
  (str (config/es-server) table))

(def operators
  #{"="
    "<"
    "<="
    ">"
    ">="
    "!="
    "in"
    "between"
    "not"})

(defn- op->op-key
  [op]
  (get {"=" :equals
        "<" :less-than
        "<=" :less-than-or-equals
        ">" :greater-than
        ">=" :greater-than-or-equals
        "!=" :not-equals
        "in" :in-set
        "between" :in-range}
       op))

(defmulti where
  (fn [_ op _] (op->op-key op)))

(defmethod where :equals
  [field _ value]
  (let [field (str field ".keyword")]
    {:term {(keyword field) value}}))

(defmethod where :less-than
  [field _ value]
  {:range {(keyword field) {:lt value}}})

(defmethod where :less-than
  [field _ value]
  {:range {(keyword field) {:lte value}}})

(defmethod where :greater-than
  [field _ value]
  {:range {(keyword field) {:gt value}}})

(defmethod where :greater-than
  [field _ value]
  {:range {(keyword field) {:gte value}}})

(defmethod where :in-range
  [field _ [lte gte]]
  {:range {(keyword field) {:lte lte :gte gte}}})

(defmethod where :not-equals
  [field _ value]
  (where field :equals value))

(defmethod where :in-set
  [field _ values]
  (let [field (str field ".keyword")]
    {:term {(keyword field) values}}))