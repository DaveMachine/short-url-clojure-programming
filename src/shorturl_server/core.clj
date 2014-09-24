(ns shorturl-server.core
  (:use [compojure.core :only [GET PUT POST defroutes]])
  (:require [compojure.handler :as handler :only [api site]]
            [compojure.route :as route :only [not-found]]
            [ring.util.response :as ring-response :only [redirect]]
            [ring.adapter.jetty :as ring-jetty :only [run-jetty]]
            [ring.middleware.defaults :as ring-def :only [wrap-defaults site-defaults secure-site-defaults]]
            [ring.middleware.basic-authentication :as ring-auth :only [wrap-basic-authentication]]
            ))

(def ^:private counter (atom 0))

(def ^:private mappings (ref {}))

(defn url-for
  "Retrieve the URL for a certain ID"
  [id]
  (@mappings id))

(defn shorten!
  "Stores the given URL under a new unique identifier, or the given
  identifier if provided. Returns the identifier as a sting. Modifies the
  global mapping accordingly."
  ([url]
     (let [id (swap! counter inc)
           id (Long/toString id 36)]
       (or (shorten! url id)
           (recur url))) )
  ([url id]
     (dosync
      (when-not (url-for id)
        (alter mappings assoc id url)
        id))))

;;(shorten! "http://a.website")
;;(shorten! "http://a.website" "a-short-id")
;;(shorten! "http://another.website" "a-short-id")
;;@mappings

(defn retain
  "Store the url"
  [& [url id :as args]]
  (if-let [id (apply shorten! args)]
    {:status 201
     :headers {"Location" id}
     :body (format "URL %s assigned the short identifier %s\n" url id)}
    {:status 409
     :body (format "Short URL %s is already taken.\n" id)}))

(defn redirect
  "Redirect to the actual url, if it exists"
  [id]
  (if-let [url (url-for id)]
    (ring-response/redirect url)
    {:status 404
     :body (format "No such short URL: %s\n" id)}))

(defroutes routes
  (GET "/" [] "Welcome!")
  (PUT "/:id" [url id] (retain url id))
  (POST "/" [url] (retain url))
  (GET "/:id" [id] (redirect id))
  (GET "/list/" [] (concat (interpose "\n" (keys @mappings)) "\n"))
  (route/not-found "Sorry, there's nothing here.\n"))

(defn authenticated? [name pass]
  (and (= name "user")
       (= pass "pass")))

(def app
  (-> routes
      ;;(ring-def/wrap-defaults ring-def/site-defaults)
      (ring-auth/wrap-basic-authentication authenticated?)
      handler/site))

;;(def server (ring-jetty/run-jetty #'app {:port 8080 :join? false}))
;;(.stop server)
