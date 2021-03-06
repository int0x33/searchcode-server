package com.searchcode.app.dao;

import com.searchcode.app.config.IDatabaseConfig;
import com.searchcode.app.config.Values;
import com.searchcode.app.dto.ConnStmtRs;
import com.searchcode.app.model.RepoResult;
import com.searchcode.app.service.CacheSingleton;
import com.searchcode.app.service.Singleton;
import com.searchcode.app.util.Helpers;
import com.searchcode.app.util.LoggerWrapper;
import org.cache2k.Cache;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class MySQLRepo implements IRepo {

    private final IDatabaseConfig dbConfig;
    private final Helpers helpers;
    private final LoggerWrapper logger;

    private final Cache<String, Optional<RepoResult>> cache;
    private final String CachePrefix = "d.m.";

    public MySQLRepo() {
        this(Singleton.getDatabaseConfig(), Singleton.getHelpers(), Singleton.getLogger(), CacheSingleton.getRepoResultCache());
    }

    public MySQLRepo(IDatabaseConfig dbConfig, Helpers helpers, LoggerWrapper loggerWrapper, Cache<String, Optional<RepoResult>> cache) {
        this.dbConfig = dbConfig;
        this.helpers = helpers;
        this.logger = loggerWrapper;
        this.cache = cache;
    }

    @Override
    public boolean saveRepo(RepoResult repoResult) {
        var existing = this.getRepoByUrl(repoResult.getUrl());
        var connStmtRs = new ConnStmtRs();
        var isNew = false;

        // Update with new details
        try {
            connStmtRs.conn = this.dbConfig.getConnection();
            if (existing.isPresent()) {
                connStmtRs.stmt = connStmtRs.conn.prepareStatement("UPDATE `repo` SET `name`=?,`scm`=?,`url`=?,`suggestedname`=?,`sourceurl`=?,`instructions`=?,`sourceid`=?,`spdx`=?,`username`=? WHERE `id`=?");
                connStmtRs.stmt.setInt(10, repoResult.getRowId());
            } else {
                isNew = true;
                connStmtRs.stmt = connStmtRs.conn.prepareStatement("INSERT INTO `repo`(`id`, `name`, `scm`, `url`, `suggestedname`, `sourceurl`, `instructions`, `sourceid`, `spdx`, `username`) VALUES (null,?,?,?,?,?,?,?,?,?);");
            }

            connStmtRs.stmt.setString(1, repoResult.getName());
            connStmtRs.stmt.setString(2, repoResult.getScm());
            connStmtRs.stmt.setString(3, repoResult.getUrl());
            connStmtRs.stmt.setString(4, Values.EMPTYSTRING); // suggestedurl
            connStmtRs.stmt.setString(5, Values.EMPTYSTRING); // sourceurl
            connStmtRs.stmt.setString(6, Values.EMPTYSTRING); // instructions
            connStmtRs.stmt.setInt(7, 1); // TODO populate this sourceid
            connStmtRs.stmt.setString(8, Values.EMPTYSTRING); // spdx
            connStmtRs.stmt.setString(9, Values.EMPTYSTRING); // username

            connStmtRs.stmt.execute();
        } catch (SQLException ex) {
            this.logger.severe(String.format("6b1285b5::error in class %s exception %s searchcode was unable to add repository %s, this is unlikely to break anything but there should be other errors in the logs", ex.getClass(), ex.getMessage(), repoResult.getName()));
        } finally {
            this.helpers.closeQuietly(connStmtRs, this.dbConfig.closeConnection());
        }

        return isNew;
    }

    @Override
    public void deleteRepoByName(String repositoryName) {
    }

    @Override
    public boolean deleteRepoById(int repoId) {
        var connStmtRs = new ConnStmtRs();

        try {
            connStmtRs.conn = this.dbConfig.getConnection();
            connStmtRs.stmt = connStmtRs.conn.prepareStatement("DELETE FROM repo WHERE id = ?;");
            connStmtRs.stmt.setInt(1, repoId);
            connStmtRs.stmt.execute();
        } catch (SQLException ex) {
            this.logger.severe(String.format("679548ab::error in class %s exception %s searchcode was unable to delete repository by id %s", ex.getClass(), ex.getMessage(), repoId));
            return false;
        } finally {
            this.helpers.closeQuietly(connStmtRs, this.dbConfig.closeConnection());
        }

        return true;
    }

    @Override
    public Optional<RepoResult> getRepoById(int repoId) {
        var cacheResult = this.cache.peekEntry(CachePrefix + repoId);
        if (cacheResult != null) {
            return cacheResult.getValue();
        }

        Optional<RepoResult> result = Optional.empty();
        var connStmtRs = new ConnStmtRs();

        try {
            connStmtRs.conn = this.dbConfig.getConnection();
            connStmtRs.stmt = connStmtRs.conn.prepareStatement("select `id`, `name`, `scm`, `url`, `suggestedname`, `sourceurl`, `instructions`, `sourceid`, `spdx`, `username` from repo where id = ?;");
            connStmtRs.stmt.setInt(1, repoId);
            connStmtRs.rs = connStmtRs.stmt.executeQuery();

            while (connStmtRs.rs.next()) {
                result = buildRepoResult(connStmtRs);
            }
        } catch (SQLException ex) {
            this.logger.severe(String.format("2b1ad83d::error in class %s exception %s searchcode was unable to get repository by id %s, this is likely to cause indexing issues and its likely other issues will be in the logs", ex.getClass(), ex.getMessage(), repoId));
        } finally {
            this.helpers.closeQuietly(connStmtRs, this.dbConfig.closeConnection());
        }

        if (result.isPresent()) {
            this.cache.put(CachePrefix + repoId, result);
        }

        return result;
    }

    @Override
    public Optional<RepoResult> getRepoByUrl(String repositoryUrl) {
        if (repositoryUrl == null) {
            return Optional.empty();
        }

        var cacheResult = this.cache.peekEntry(CachePrefix + repositoryUrl);
        if (cacheResult != null) {
            return cacheResult.getValue();
        }

        Optional<RepoResult> result = Optional.empty();
        var connStmtRs = new ConnStmtRs();

        try {
            connStmtRs.conn = this.dbConfig.getConnection();
            connStmtRs.stmt = connStmtRs.conn.prepareStatement("select `id`, `name`, `scm`, `url`, `suggestedname`, `sourceurl`, `instructions`, `sourceid`, `spdx`, `username` from repo where url = ?;");
            connStmtRs.stmt.setString(1, repositoryUrl);
            connStmtRs.rs = connStmtRs.stmt.executeQuery();

            while (connStmtRs.rs.next()) {
                result = buildRepoResult(connStmtRs);
            }
        } catch (SQLException ex) {
            this.logger.severe(String.format("fb6e9dfe::error in class %s exception %s searchcode was unable to get repository by url %s, this is likely to cause indexing issues and its likely other issues will be in the logs", ex.getClass(), ex.getMessage(), repositoryUrl));
        } finally {
            this.helpers.closeQuietly(connStmtRs, this.dbConfig.closeConnection());
        }

        if (result.isPresent()) {
            this.cache.put(CachePrefix + repositoryUrl, result);
        }

        return result;
    }

    @Override
    public Optional<RepoResult> getRepoByName(String repositoryName) {
        return Optional.empty();
    }

    @Override
    public int getRepoCount() {
        return 0;
    }

    @Override
    public List<RepoResult> getPagedRepo(int offset, int pageSize) {
        return null;
    }

    @Override
    public List<RepoResult> searchRepo(String searchTerms) {
        return null;
    }

    @Override
    public List<RepoResult> getAllRepo() {
        return null;
    }

    private Optional<RepoResult> buildRepoResult(ConnStmtRs connStmtRs) throws SQLException {
        var rowId = connStmtRs.rs.getInt("id");
        var repoName = connStmtRs.rs.getString("name");
        var repoScm = connStmtRs.rs.getString("scm");
        var repoUrl = connStmtRs.rs.getString("url");

        var repoResult = new RepoResult()
                .setRowId(rowId)
                .setName(repoName)
                .setScm(repoScm)
                .setUrl(repoUrl);

        return Optional.of(repoResult);
    }
}
