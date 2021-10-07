(ns org.clojars.punit-naik.sqles.parse-sql.utils)

(defmulti handle-clause-data
  (fn [clause _] clause))

(defmethod handle-clause-data :default
  [_ _])
