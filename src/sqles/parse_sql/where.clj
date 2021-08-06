(ns sqles.parse-sql.where
  (:require [sqles.parse-sql.utils :refer [handle-clause-data]]))

(defmethod handle-clause-data "where"
  [_ clause-data]
  clause-data)