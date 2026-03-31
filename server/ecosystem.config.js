module.exports = {
  apps: [{
    name: 'blesk-server',
    script: 'src/index.js',
    instances: 1,
    max_memory_restart: '512M',
    min_uptime: '10s',
    max_restarts: 15,
    restart_delay: 3000,
    env: {
      NODE_ENV: 'production',
    },
    log_date_format: 'YYYY-MM-DD HH:mm:ss',
    error_file: 'logs/error.log',
    out_file: 'logs/out.log',
    merge_logs: true,
    time: true,
  }]
};
