(ns org.clojars.punit-naik.sqles.core-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is]]
   [org.clojars.punit-naik.sqles.config :as config]
   [org.clojars.punit-naik.sqles.core :as core]
   [org.clojars.punit-naik.sqles.parse-sql :as sql])
  (:import
   [java.net UnknownHostException ConnectException]
   [org.apache.http ProtocolException]))

(deftest method->http-fn-test
  (is (= http/post (core/method->http-fn :post)))
  (is (= http/get (core/method->http-fn :get)))
  (is (= http/delete (core/method->http-fn :delete)))
  (is (= http/put (core/method->http-fn :put))))

(deftest run-query-test
  (testing "`run-query` fn tests"
    (with-redefs [http/get (fn [_ _]
                             {:body (json/generate-string {:version {:number "7.10.2"}
                                                           :tagline "You Know, for Search"})
                              :status 200})
                  http/post (fn [_ _]
                              (throw (ex-info "boom"
                                              {:status 405
                                               :body (json/generate-string
                                                      {:error "Incorrect HTTP method for uri [/] and method [POST], allowed: [GET]"})})))]
      (let [{:keys [status]
             {:keys [tagline]
              {:keys [number]} :version} :body}
            (core/run-query {:url "http://localhost:9200/"})]
        (is (= 200 status))
        (is (re-matches #"\d+\.\d+\.\d+" number))
        (is (= tagline "You Know, for Search")))
      (let [{:keys [status] {error-msg :error} :body}
            (core/run-query {:url "http://localhost:9200" :method :post})]
        (is (= status 405))
        (is (str/starts-with? error-msg "Incorrect HTTP method for uri [/] and method [POST], allowed: "))))))

(deftest run-query-success-get-test
  (let [response-body {:acknowledged true}]
    (with-redefs [http/get (fn [_ _]
                             {:body (json/generate-string response-body)
                              :status 200})]
      (is (= {:body response-body :status 200}
             (core/run-query {:url "http://localhost:9200/"
                              :method :get
                              :query-params {:pretty true}}))))))

(deftest run-query-success-post-test
  (let [response-body {:ok true}]
    (with-redefs [http/post (fn [_ _]
                              {:body (json/generate-string response-body)
                               :status 201})]
      (is (= {:body response-body :status 201}
             (core/run-query {:url "http://localhost:9200/_search"
                              :method :post
                              :body {:query {:match_all {}}}}))))))

(deftest run-query-success-put-delete-test
  (let [put-body {:updated true}
        del-body {:deleted true}]
    (with-redefs [http/put (fn [_ _]
                             {:body (json/generate-string put-body)
                              :status 200})
                  http/delete (fn [_ _]
                                {:body (json/generate-string del-body)
                                 :status 200})]
      (is (= {:body put-body :status 200}
             (core/run-query {:url "http://localhost:9200/test/_doc/1"
                              :method :put
                              :body {:doc {:a 1}}})))
      (is (= {:body del-body :status 200}
             (core/run-query {:url "http://localhost:9200/test/_doc/1"
                              :method :delete}))))))

(deftest run-query-exceptioninfo-test
  (let [ex (ex-info "boom" {:status 400
                            :body (json/generate-string {:error "bad request"})})]
    (with-redefs [http/get (fn [_ _] (throw ex))]
      (is (= {:status 400 :body {:error "bad request"}}
             (core/run-query {:url "http://localhost:9200/"
                              :method :get}))))))

(deftest run-query-known-exceptions-test
  (testing "known exceptions return empty map"
    (with-redefs [core/method->http-fn (constantly (fn [_ _] (throw (UnknownHostException.))))]
      (is (= {} (core/run-query {:url "http://bad-host" :method :get}))))
    (with-redefs [core/method->http-fn (constantly (fn [_ _] (throw (ProtocolException.))))]
      (is (= {} (core/run-query {:url "http://bad-protocol" :method :get}))))
    (with-redefs [core/method->http-fn (constantly (fn [_ _] (throw (ConnectException.))))]
      (is (= {} (core/run-query {:url "http://no-conn" :method :get}))))
    (with-redefs [core/method->http-fn (constantly (fn [_ _] (throw (IllegalArgumentException.))))]
      (is (= {} (core/run-query {:url "http://bad-arg" :method :get}))))))

(deftest main-test
  (let [printed (atom nil)]
    (with-redefs [sql/parse-query (constantly {:url "http://localhost:9200/test/_search"
                                               :body {:query {:match_all {}}}
                                               :method :post})
                  core/run-query (constantly {:status 200 :body {:ok true}})
                  clojure.pprint/pprint (fn [v] (reset! printed v))]
      (core/-main "select" "*" "from" "test")
      (is (= {:status 200 :body {:ok true}} @printed)))))
