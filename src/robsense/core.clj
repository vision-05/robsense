(ns robsense.core
  (:gen-class)
  (:require [clojure.string :as string]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :as cors]
            [compojure.core :as compojure]
            [compojure.route :as croute]
            [clojure.data.json :as json]
            [udp-wrapper.core :as udp]))

(def latest (atom {:ref1b 0 :ref2b 0 :ref3b 0 :ref4b 0 :ref5b 0 :ref1c 0 :ref2c 0 :ref3c 0 :ref4c 0 :ref5c 0 :distf 0 :distl 0 :distr 0 :distb 0 :log "none"}))

(defn handler [msg]
  (println msg)
  (let [n (first msg)
        qual (second msg)
        m (apply str (rest (rest msg)))]
    (if (= n \l) (swap! latest assoc :log (apply str (rest msg))) (if (= n \d) (swap! latest assoc (keyword (str "dist" qual)) m)
      (swap! latest assoc (keyword (str "ref" n qual)) m)))))

(def socket (udp/create-udp-server 2300))
(def my-future (udp/receive-loop socket (udp/empty-packet 512) handler))

(defn get-readings [s]
  (string/split s))

(defn make-packet [s i p]
  (udp/packet (udp/get-bytes-utf8 s) (udp/make-address i) (Integer/parseInt p)))

(defn get-sensor [request]
  (let [id (:id (:params request))]
    (str "Sensor: " id)))

(defn get-sensors [request]
  (let [resp {:status 200
              :headers {"Content-Type" "text/html"}
              :body (json/write-str {:ref1b (:ref1b @latest) :ref2b (:ref2b @latest) :ref3b (:ref3b @latest) :ref4b (:ref4b @latest) :ref5b (:ref5b @latest) :ref1c (:ref1c @latest) :ref2c (:ref2c @latest) :ref3c (:ref3c @latest) :ref4c (:ref4c @latest) :ref5c (:ref5c @latest) :distf (:distf @latest) :distl (:distl @latest) :distr (:distr @latest) :distb (:distb @latest) :log (:log @latest)})}]
    (println "REQ" resp)
    resp))

(defn kill-switch [request]
  (println "kill")
  (let [ip (:ip (:params request))
        port (:port (:params request))]
    (println ip port)
    (udp/send-message socket (udp/packet (udp/get-bytes-utf8 "k") (udp/make-address ip) (Integer/parseInt port)))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "KILLED"}))

(defn unkill-switch [request]
  (println "unkill")
  (let [ip (:ip (:params request))
        port (:port (:params request))]
    (println ip port)
    (udp/send-message socket (udp/packet (udp/get-bytes-utf8 "f") (udp/make-address ip) (Integer/parseInt port)))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "KILLED"}))

(defn p-const [req]
  (let [params (:params req)
        ip (:ip params)
        port (:port params)
        v (parse-double (:val params))]
    (println "PD")
    (udp/send-message socket (make-packet (str "p" (* 100 v)) ip port))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "Pd"}))

(defn i-const [req]
  (let [params (:params req)
        ip (:ip params)
        port (:port params)
        v (parse-double (:val params))]
    (udp/send-message socket (make-packet (str "i" (* 100 v)) ip port))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "Id"}))

(defn d-const [req]
  (let [params (:params req)
        ip (:ip params)
        port (:port params)
        v (parse-double (:val params))]
    (udp/send-message socket (make-packet (str "d" (* 100 v)) ip port))
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body "Dd"}))


(compojure/defroutes app-routes
  (compojure/GET "/" [] "Hello World!")
  (compojure/GET "/sensor/:id" params get-sensor)
  (compojure/GET "/sensors" params get-sensors)
  (compojure/POST "/kill/:ip/:port" [] kill-switch)
  (compojure/POST "/unkill/:ip/:port" [] unkill-switch)
  (compojure/POST "/p/:ip/:port/:val" [] p-const)
  (compojure/POST "/i/:ip/:port/:val" [] i-const)
  (compojure/POST "/d/:ip/:port/:val" [] d-const)
  (croute/not-found "Error 404: Page not found"))

(def app (-> app-routes (cors/wrap-cors :access-control-allow-origin [#".*"] :access-control-allow-methods [:get :post :put :delete])))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (jetty/run-jetty app
                   {:port 3000
                    :join? true}))
