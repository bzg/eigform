(ns eigform.handler
  (:gen-class)
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :as params]
            [ring.middleware.multipart-params :as multipart-params]
            [ring.middleware.keyword-params :as keyword-params]
            [compojure.core :as compojure :refer (GET POST defroutes)]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [postal.core :as postal]
            [hiccup.page :as h]
            [hiccup.element :as he]
            [eigform.config :refer :all]
            [clojure.java.io :as io]
            [clojure.core.async :as async])
  (:import [java.io File]))

(defn home-page []
  (h/html5
   {:lang "fr"}
   [:head
    [:title "Candidature EIG"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (h/include-css
     "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
     "css/index.css")]
   [:body
    [:div {:class "container"}
     [:h1 "Candidature EIG"]
     [:p "Un paragraphe d'explication."]
     [:h2 "Qui êtes-vous ?"]
     [:form
      {:action "/file" :method "post" :enctype "multipart/form-data"}
      [:label "Nom :"]
      [:div {:class "form-group"}
       [:input {:placeholder "Proust"
                :class       "form-control" :name "nom" :size "100" :autofocus true}]]
      [:label "Prénom :"]
      [:div {:class "form-group"}
       [:input {:placeholder "Marcel"
                :class       "form-control" :name "prenom" :size "100"}]]
      [:label "Adresse de courriel :"]
      [:div {:class "form-group"}
       [:input {:placeholder "bzg@bzg.fr"
                :class       "form-control" :name "email" :size "100" :type "email" :required true}]]
      [:label "Votre CV (pdf) :"]
      [:div {:class "form-group"}
       [:input {:name   "file" :type "file" :size "200" :required true
                :accept "application/pdf"}]]
      [:div {:class "form-group"}
       [:input {:type "submit" :value "Envoyer" :class "btn btn-danger btn-lg pull-right"}]]]]]))

(defn result-page [email file]
  (h/html5
   {:lang "fr"}
   [:head
    [:title "Candidature EIG"]
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (h/include-css
     "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
     "css/index.css")]
   [:body
    [:div {:class "container"}
     [:h1 "Merci !"]
     [:p (str "Nous avons envoyé un email à " email " pour accuser bonne réception de votre candidature avec le fichier : " file)]]]))

(defn send-mail [email file]
  (postal/send-message
   {:host "smtp.mailgun.org"
    :port 587
    :user "postmaster@eig-forever.org"
    :pass (mailgun-password)}
   {:from    "postmaster@eig-forever.org"
    :to      email
    :subject "Accusé réception de votre candidature"
    :body    (str "Bonjour,

merci pour votre candidature EIG.

Votre nom de fichier : " file "

-- 
 L'équipe EIG")}))

(def email-channel (async/chan 10))

(defn start-email-loop []
  (async/go
    (loop [message (async/<! email-channel)]
      (try
        (send-mail (:email message) (:file message))
        (catch Throwable e
          ;; FIXME (logger/error ...)
          ))
      (recur (async/<! email-channel)))))

;; (defn- handle-upload [actual-file dest-file dest-dir]
;;   (io/copy actual-file (io/file dest-file))
;;   (.mkdir (io/file dest-dir))
;;   (export/unzip-file dest-file dest-dir)
;;   (io/delete-file (io/file dest-file))
;;   (gertrude/create-dirs-and-mv-files dest-dir))

(defn- html-response [body]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    body})

(defroutes app-routes
  (GET "/" [] (home-page))
  ;; FIXME: [email file :as param {params :params}]?
  (POST "/file" [nom prenom email file]
        (let [file-name (:filename file)
              ;; actual-file (:tempfile file)
              ;; rel-dir     (str (java.util.UUID/randomUUID) "/")
              ;; dest-file   (str (export-dir) file-name)
              ;; dest-dir    (str (export-dir) rel-dir)
              ]
          ;; (handle-upload actual-file dest-file dest-dir)
          ;; (gertrude/to-csv-file (str dest-dir "AllEntities.xml")
          ;;                       (str dest-dir "index.csv"))
          ;; (export/export-index-csv dest-dir "index.csv"
          ;;                          {:title                title
          ;;                           :gertrude-diff-prefix url-diff
          ;;                           :href                 url
          ;;                           :label                copyrights})
          (async/go
            (async/>!
             email-channel {:email email :file file-name}))
          (html-response (result-page email file-name))))
  (route/resources "/")
  (route/not-found "404 error"))

(def app (-> app-routes
             reload/wrap-reload
             params/wrap-params
             keyword-params/wrap-keyword-params
             multipart-params/wrap-multipart-params))

(defn -main [& args]
  (do
    (start-email-loop)
    (http-kit/run-server #'app {:port (port) :max-body 1000000000})))


