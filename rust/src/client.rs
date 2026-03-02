#![allow(dead_code)]

use anyhow::{Result, anyhow};
use reqwest::{
    blocking::Client as ReqwestClient,
    header::{CONTENT_TYPE, HeaderMap, HeaderValue},
};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::{
    collections::HashMap,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

pub const MAX_SEED: u64 = 1 << 63;

// Action names
pub const PLACE: &str = "place";
pub const MOVE: &str = "move";
pub const PICKUP: &str = "pickup";
pub const DISCARD: &str = "discard";

// Action is a json-friendly representation of an action.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "lowercase")]
pub struct Action {
    /// unix timestamp in microseconds
    pub timestamp: u64,
    /// order id
    pub id: String,
    /// place, move, pickup, or discard
    pub action: String,
    /// heater, cooler, or shelf. Target is the destination for move.
    pub target: String,
}

// Temperatures
pub const HOT: &str = "hot";
pub const COLD: &str = "cold";
pub const ROOM: &str = "room";

// Targets
pub const HEATER: &str = "heater";
pub const COOLER: &str = "cooler";
pub const SHELF: &str = "shelf";

#[derive(Debug, Clone, Deserialize)]
pub struct Order {
    /// order id
    pub id: String,
    /// food name
    pub name: String,
    /// ideal temperature
    pub temp: String,
    /// price in dollars
    #[serde(default)]
    pub price: u64,
    /// freshness in seconds
    pub freshness: u64,
}

impl Action {
    pub fn new(id: &str, action_type: &str, target: &str, timestamp: SystemTime) -> Self {
        Self {
            action: action_type.to_string(),
            id: id.to_string(),
            target: target.to_string(),
            timestamp: timestamp
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_micros()
                .try_into()
                .unwrap(),
        }
    }
}

/// Client is a client for fetching and solving challenge test problems
#[derive(Debug)]
pub struct Client {
    client: ReqwestClient,
    endpoint: String,
    auth: String,
}

impl Client {
    pub fn new(endpoint: &str, auth: &str) -> Self {
        Self {
            client: ReqwestClient::new(),
            endpoint: endpoint.to_string(),
            auth: auth.to_string(),
        }
    }

    /// New fetches a new test problem from the server. The URL also works in a browser for convenience.
    pub fn challenge(&mut self, name: &str, seed: u64) -> Result<(Vec<Order>, String)> {
        let seed = (if seed == 0 {
            rand::random_range(0..MAX_SEED)
        } else {
            seed
        })
        .to_string();

        let mut query_params =
            HashMap::from([("seed", seed.as_str()), ("auth", self.auth.as_str())]);

        if !name.is_empty() {
            query_params.insert("name", name);
        }

        let url = reqwest::Url::parse_with_params(
            &format!("{}/interview/challenge/new", &self.endpoint),
            query_params.iter(),
        )?;

        let response = self
            .client
            .get(url.clone())
            .timeout(Duration::from_secs(5))
            .send()?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().unwrap_or_default();
            return Err(anyhow!(
                "failed to fetch new test problem: {}: {}",
                status,
                body
            ));
        }

        let test_id = response
            .headers()
            .get("x-test-id".to_string())
            .and_then(|v| v.to_str().ok().map(ToString::to_string))
            .unwrap_or_default();

        let orders = response.json()?;

        println!("Fetched new test problem, id={}: {}", test_id, url);
        Ok((orders, test_id))
    }

    /// Solve submits a sequence of actions and parameters as a solution to a test problem. Returns test result.
    pub fn solve(
        &mut self,
        test_id: &str,
        rate: Duration,
        min: Duration,
        max: Duration,
        actions: &[Action],
    ) -> Result<String> {
        let query = HashMap::from([("auth", &self.auth)]);

        let mut headers = HeaderMap::new();
        headers.insert("x-test-id", HeaderValue::from_str(test_id)?);
        headers.insert(CONTENT_TYPE, HeaderValue::from_str("application/json")?);

        let body = json!({
            "options": {
                "rate": rate.as_micros(),
                "min": min.as_micros(),
                "max": max.as_micros(),
            },
            "actions": actions
        });

        let response = self
            .client
            .post(format!("{}/interview/challenge/solve", &self.endpoint))
            .headers(headers)
            .query(&query)
            .json(&body)
            .timeout(Duration::from_secs(5))
            .send()?;

        if !response.status().is_success() {
            let status = response.status();
            let body = response.text().unwrap_or_default();
            return Err(anyhow!(
                "failed to submit solution: {}: {}",
                status,
                body
            ));
        }

        response
            .text()
            .map_err(|_| anyhow!("failed to submit test solution"))
    }
}
