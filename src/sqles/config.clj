(ns sqles.config
  (:require [omniconf.core :as cfg]
            [clojure.string :as str]
            [clj-http.client :as client]
            [cheshire.core :as json]))

(defmacro when-let-multiple
  [bindings & body]
  `(let ~bindings
     (if (and ~@(take-nth 2 bindings))
       (do ~@body))))

(cfg/define
  {:es-protocol {:description "Protocol to contact the ES Server"
                 :type :string
                 :one-of ["http" "https"]
                 :default "http"}
   :es-hostname {:description "Where service is deployed"
                 :type :string
                 :required true}
   :es-port {:description "HTTP port"
             :type :number
             :required true}
   :es-username {:description "Username for the ES Server"
                 :type :string}
   :es-password {:description "Password for the ES Server"
                 :type :string
                 :secret true}
   :conf {:description "Configuration file"
          :type :file
          :verifier cfg/verify-file-exists}})

(cfg/populate-from-properties)
(when-let [conf (cfg/get :conf)]
  (cfg/populate-from-file conf))
(cfg/populate-from-env)
(cfg/verify :quit-on-error true)

(defn add-trailing-backslash
  [link]
  (if-not (str/ends-with? link "/")
    (str link "/") link))

(defn ping-server
  [server]
  (try (let [server-url (str (add-trailing-backslash server) "_cluster/health")
             {:keys [body status]} (client/get server-url {:accept :json})]
         {:body (json/parse-string body true)
          :status status})
       (catch Exception _ {:status 405})))

(defn server-up?
  [server]
  (let [{:keys [status] {:keys [active_shards]} :body} (ping-server server)]
    (and (= status 200)
         (>= active_shards 1))))

(defn generate-server-url-from-config
  []
  (str (cfg/get :es-protocol) "://"
       (when-let-multiple
        [username (cfg/get :es-username)
         password (cfg/get :es-password)]
        (str username ":" password "@"))
       (cfg/get :es-hostname)
       (when-let [port (cfg/get :es-port)]
         (str ":" port))))

(defn es-server
  []
  (let [url (generate-server-url-from-config)
        is-server-up? (server-up? url)]
    (when is-server-up?
      url)))