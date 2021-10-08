(ns org.clojars.punit-naik.sqles.core
  (:require [cheshire.core :as json]
            [clj-http.client :as http])
  (:import [clojure.lang ExceptionInfo]
           [java.lang IllegalArgumentException]
           [java.net UnknownHostException]
           [org.apache.http ProtocolException]
           [java.net ConnectException]))

(defmulti method->http-fn
  (fn [method] method))

(defmethod method->http-fn
  :post
  [_]
  http/post)

(defmethod method->http-fn
  :get
  [_]
  http/get)

(defmethod method->http-fn
  :delete
  [_]
  http/delete)

(defmethod method->http-fn
  :put
  [_]
  http/put)

(defn run-query
  [{:keys [url body method query-params]
    :or {method :get
         query-params {}}}]
  (when (seq url)
    (try
      (let [post? (= method :post)
            get? (= method :get)
            {response :body status :status}
            ((method->http-fn method)
             url
             (cond-> {:accept :json}
               post? (merge {:content-type :json
                             :body (json/generate-string body)})
               get? (merge {:query-params query-params})))]
        {:body (json/parse-string response true)
         :status status})
      (catch ExceptionInfo e
        (let [{ex-status :status ex-body :body} (ex-data e)]
          {:status ex-status
           :body (json/parse-string ex-body true)}))
      (catch UnknownHostException _ {})
      (catch ProtocolException _ {})
      (catch ConnectException _ {})
      (catch IllegalArgumentException _ {}))))

(defn -main
  [& args]
  (println "No Op"))