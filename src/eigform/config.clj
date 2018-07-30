(ns eigform.config
  (:require [aero.core :as aero]))

(def config (aero/read-config "config.edn"))

(defn mailgun-password []
  (get-in config [:secrets :mailgun-password]))

(defn export-dir []
  "resources/public/exports/")

(defn port []
  (get-in config [:port]))
