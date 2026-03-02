use anyhow::Result;
use clap::Parser;
use client::{Action, MAX_SEED};
use ctrlc;
use std::time::{Duration, SystemTime};

mod client;

#[derive(Parser)]
struct Args {
    #[arg(
        long,
        default_value = "https://api.cloudkitchens.com",
        help = "Challenge server endpoint"
    )]
    pub endpoint: String,

    #[arg(long, help = "Authorization token (required)")]
    pub auth: String,

    #[arg(short, long, default_value_t = String::default(), help = "Problem name (optional)")]
    pub name: String,

    #[arg(
        short,
        long,
        default_value = "0",
        value_parser = clap::value_parser!(u64).range(0..MAX_SEED),
        help = "Problem seed (optional)"
    )]
    pub seed: u64,

    #[arg(
        short,
        long,
        default_value = "500",
        help = "Inverse order rate in milliseconds"
    )]
    rate: u64,

    #[arg(long, default_value = "4", help = "Minimum pickup time in seconds")]
    min: u64,

    #[arg(long, default_value = "8", help = "Maximum pickup time in seconds")]
    max: u64,
}

fn main() -> Result<()> {
    ctrlc::set_handler(|| std::process::exit(1)).expect("Error setting Ctrl-C handler");
    let args = Args::try_parse()?;

    let rate = Duration::from_millis(args.rate);
    let min = Duration::from_secs(args.min);
    let max = Duration::from_secs(args.max);

    let mut client = client::Client::new(&args.endpoint, &args.auth);
    let (orders, test_id) = client.challenge(&args.name, args.seed)?;

    // ------ Execution harness logic goes here using rate, min and max ------

    let mut actions = vec![];
    for order in orders {
        println!("Received: {:?}", order);

        actions.push(Action::new(
            &order.id,
            client::PLACE,
            client::COOLER,
            SystemTime::now(),
        ));
        std::thread::sleep(rate)
    }

    // ------------------------------------------------------------------------

    let result = client.solve(&test_id, rate, min, max, &actions)?;

    println!("Test result: {result}");
    Ok(())
}
