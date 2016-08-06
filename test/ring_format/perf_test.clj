(ns ring-format.perf-test
  (:require [criterium.core :as cc]
            [ring-format.core :as rfc]))

;;
;; start repl with `lein perf repl`
;; perf measured with the following setup:
;;
;; Model Name:            MacBook Pro
;; Model Identifier:      MacBookPro11,3
;; Processor Name:        Intel Core i7
;; Processor Speed:       2,5 GHz
;; Number of Processors:  1
;; Total Number of Cores: 4
;; L2 Cache (per Core):   256 KB
;; L3 Cache:              6 MB
;; Memory:                16 GB
;;

(set! *warn-on-reflection* true)

(defn title [s]
  (println
    (str "\n\u001B[35m"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\n## " s " ##\n"
         (apply str (repeat (+ 6 (count s)) "#"))
         "\u001B[0m\n")))

(def +json-request+
  {:headers {"content-type" "application/json"
             "accept" "application/json"}
   :body "{\"kikka\": 42}"})

(def +json-response+
  {:status 200
   :body {:kukka 24}})

(def +transit-json-request+
  {:headers {"content-type" "application/transit+json"
             "accept" "application/transit+json"}
   :body "kikka"})

;;
;; naive
;;

(defn old []

  (let [match? (fn [regexp]
                 (fn [{:keys [body] :as req}]
                   (if-let [^String type (get req :content-type
                                              (get-in req [:headers "Content-Type"]
                                                      (get-in req [:headers "content-type"])))]
                     (and body (not (empty? (re-find regexp type)))))))
        api-request? (some-fn
                       (match? #"^application/(vnd.+)?json")
                       (match? #"^application/(vnd.+)?(x-)?(clojure|edn)")
                       (match? #"^application/(vnd.+)?(x-)?msgpack")
                       (match? #"^(application|text)/(vnd.+)?(x-)?yaml")
                       (match? #"^application/(vnd.+)?(x-)?transit\+json")
                       (match? #"^application/(vnd.+)?(x-)?transit\+msgpack"))]

    ;; 551ns
    (title "R-M-F: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 2520ns
    (title "R-M-F: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+)))

  (let [match? (fn [type]
                 (fn [req] (and (= (get-in req [:headers "content-type"]) type)
                                (:body req)
                                true)))
        api-request? (some-fn
                       (match? "application/json")
                       (match? "application/edn")
                       (match? "application/msgpack")
                       (match? "application/yaml")
                       (match? "application/transit+json")
                       (match? "application/transi+msgpack"))]

    ;; 93ns
    (title "NAIVE: JSON")
    (assert (api-request? +json-request+))
    (cc/quick-bench
      (api-request? +json-request+))

    ;; 665ns
    (title "NAIVE: TRANSIT")
    (assert (api-request? +transit-json-request+))
    (cc/quick-bench
      (api-request? +transit-json-request+))))

;;
;; Real
;;

(defn content-type []
  (let [{:keys [consumes matchers extract-content-type-fn]} (rfc/compile rfc/default-options)]

    ; 52ns
    ; 38ns consumes & produces (-27%)
    ; 27ns compile (-29%) (-48%)
    (title "Content-type: JSON")
    (assert (= :json (rfc/extract-content-type consumes matchers extract-content-type-fn +json-request+)))
    (cc/quick-bench
      (rfc/extract-content-type consumes matchers extract-content-type-fn +json-request+))

    ; 65ns
    ; 55ns consumes & produces (-15%)
    ; 42ns compile (-24%) (-35%)
    (title "Content-type: TRANSIT")
    (assert (= :transit-json (rfc/extract-content-type consumes matchers extract-content-type-fn +transit-json-request+)))
    (cc/quick-bench
      (rfc/extract-content-type consumes matchers extract-content-type-fn +transit-json-request+))))

(defn accept []
  (let [{:keys [consumes extract-accept-fn]} (rfc/compile rfc/default-options)]

    ; 71ns
    ; 58ns consumes & produces (-18%)
    ; 48ns compile (-17%) (-32%)
    (title "Accept: TRANSIT")
    (assert (= :transit-json (rfc/extract-accept consumes extract-accept-fn +transit-json-request+)))
    (cc/quick-bench
      (rfc/extract-accept consumes extract-accept-fn +transit-json-request+))))

(defn request []
  (let [{:keys [consumes matchers extract-content-type-fn extract-accept-fn]} (rfc/compile rfc/default-options)]

    ; 179ns
    (title "Request: JSON")
    (cc/quick-bench
      (rfc/format-request consumes matchers extract-content-type-fn extract-accept-fn +json-request+))

    ; 211ns
    (title "Request: Transit")
    (cc/quick-bench
      (rfc/format-request consumes matchers extract-content-type-fn extract-accept-fn +transit-json-request+))))

(defn decode-encode []
  (let [{:keys [consumes matchers extract-content-type-fn extract-accept-fn adapters]} (rfc/compile rfc/default-options)
        handle-request (fn [request]
                         (let [format (rfc/extract-content-type consumes matchers extract-content-type-fn +json-request+)
                               decode (-> adapters format :decode)]
                           (-> request
                               (update :body decode)
                               (assoc ::format format))))
        handle-response (fn [request response]
                          (let [format (rfc/extract-accept consumes extract-accept-fn request)
                                encode (-> adapters format :encode)]
                            (-> response
                                (update :body encode)
                                (assoc ::format format))))]

    ; 1131ns
    (title "Request - decode: JSON")
    (assert (= {:kikka 42} (:body (handle-request +json-request+))))
    (cc/quick-bench
      (handle-request +json-request+))

    ; 1204ns
    (title "Response - encode: JSON")
    (assert (= "{\"kukka\":24}" (:body (handle-response +json-request+ +json-response+))))
    (cc/quick-bench
      (handle-response +json-request+ +json-response+))))

(defn all []
  (old)
  (content-type)
  (accept)
  (request)
  (decode-encode))

(comment
  (old)
  (content-type)
  (accept)
  (request)
  (decode-encode)
  (all))
